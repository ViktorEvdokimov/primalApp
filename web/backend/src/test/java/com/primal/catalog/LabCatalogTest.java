package com.primal.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.primal.rules.model.Plant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.yaml.YAMLMapper;

@DisplayName("Каталог: планшет лаборатории")
class LabCatalogTest {

    private static final Catalog CATALOG = CatalogLoader.load();

    private static List<Set<Plant>> units(String name) {
        return CATALOG.lab().stream().filter(potion -> potion.name().equals(name)).findFirst().orElseThrow().units();
    }

    @Test
    @DisplayName("6 зелий в порядке планшета с кодами LAB_01…LAB_06")
    void potions() {
        // вызов и проверка
        assertThat(CATALOG.lab()).extracting(LabPotionDef::name)
                .containsExactly("Алемор", "Имперум", "Ирден", "Хатрокс", "Эвок", "Видья");
        assertThat(CATALOG.lab()).extracting(LabPotionDef::code)
                .containsExactly("LAB_01", "LAB_02", "LAB_03", "LAB_04", "LAB_05", "LAB_06");
    }

    @Test
    @DisplayName("пример из правил: «Имперум» — антемон и ниллея, «Эвок» — тармарет и антемон или меллис")
    void rulebookExample() {
        // вызов и проверка
        assertThat(units("Имперум")).containsExactly(Set.of(Plant.ANTHEMON), Set.of(Plant.NILLEA));
        assertThat(units("Эвок")).containsExactly(Set.of(Plant.TARMARET), Set.of(Plant.ANTHEMON, Plant.MELLIS));
    }

    @Test
    @DisplayName("остальные зелья по фото планшета; «Алемор» — любые 2 растения")
    void otherPotions() {
        // вызов и проверка
        assertThat(units("Алемор")).containsExactly(EnumSet.allOf(Plant.class), EnumSet.allOf(Plant.class));
        assertThat(units("Ирден")).containsExactly(Set.of(Plant.TARMARET), Set.of(Plant.ALBALACEA, Plant.SELICORNIA));
        assertThat(units("Хатрокс")).containsExactly(Set.of(Plant.NILLEA), Set.of(Plant.SELICORNIA));
        assertThat(units("Видья")).containsExactly(Set.of(Plant.ALBALACEA), Set.of(Plant.MELLIS));
    }

    @Test
    @DisplayName("ошибки данных: не 2 растения, неизвестное растение, не 6 зелий")
    void errors() {
        // подготовка
        YAMLMapper yaml = new YAMLMapper();

        // вызов и проверка
        assertThatThrownBy(() -> CatalogLoader.parseLab(yaml.readTree("- { name: Эвок, cost: [TARMARET] }")))
                .isInstanceOf(CatalogException.class).hasMessageContaining("lab.yaml, Эвок: на планшете 2 растения");
        assertThatThrownBy(() -> CatalogLoader.parseLab(yaml.readTree("- { name: Эвок, cost: [TARMARET, ROSE/MELLIS] }")))
                .isInstanceOf(CatalogException.class).hasMessageContaining("неизвестное растение ROSE");
        assertThatThrownBy(() -> CatalogLoader.parseLab(yaml.readTree("- { name: Эвок, cost: [TARMARET, MELLIS] }")))
                .isInstanceOf(CatalogException.class).hasMessageContaining("зелий должно быть 6, а их 1");
    }
}
