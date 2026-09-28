package com.primal.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.primal.rules.effects.Condition;
import com.primal.rules.effects.Effect;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.yaml.YAMLMapper;

@DisplayName("Каталог: ссылочная целостность")
class CatalogIntegrityTest {

    private static final Catalog CATALOG = CatalogLoader.load();

    @Test
    @DisplayName("перенесённый каталог целостен")
    void bundledCatalogIsConsistent() {
        // вызов и проверка
        assertThat(CatalogIntegrity.problems(CATALOG)).isEmpty();
    }

    @Test
    @DisplayName("ссылка на несуществующее задание, достижение или босса находится с указанием места")
    void brokenReferencesAreReported() {
        // подготовка: к заданию 1 добавлены эффекты со ссылками в никуда
        QuestDef original = CATALOG.quest(1).orElseThrow();
        List<Effect> victory = new ArrayList<>(original.victory());
        victory.add(new Effect.OpenQuest(99, null));
        victory.add(new Effect.Conditional(new Condition.HasAchievement("NO_SUCH"), List.of(new Effect.FinalBattle("NOBODY", null)),
                List.of(), null));
        List<QuestDef> quests = new ArrayList<>(CATALOG.quests());
        quests.set(0, new QuestDef(1, original.name(), original.bossCode(), null, victory, original.defeat(), "[]", "[]"));
        Catalog broken = new Catalog(CATALOG.bosses(), CATALOG.achievements(), quests, CATALOG.chapters(), CATALOG.checksum());

        // вызов
        List<String> problems = CatalogIntegrity.problems(broken);

        // проверка
        assertThat(problems).containsExactly(
                "задание 1, победа: нет задания 99",
                "задание 1, победа: нет достижения NO_SUCH",
                "задание 1, победа: нет босса NOBODY");
        assertThatThrownBy(() -> CatalogLoader.validate(broken))
                .isInstanceOf(CatalogException.class)
                .hasMessageContaining("нет задания 99");
    }

    @Test
    @DisplayName("пропущенная глава — ошибка")
    void missingChapter() {
        // подготовка
        Catalog broken = new Catalog(CATALOG.bosses(), CATALOG.achievements(), CATALOG.quests(),
                CATALOG.chapters().subList(0, 10), CATALOG.checksum());

        // вызов и проверка
        assertThat(CatalogIntegrity.problems(broken)).anyMatch(problem -> problem.startsWith("главы должны идти по порядку 1–11"));
    }

    @Test
    @DisplayName("неизвестный вид эффекта и стихия в ресурсах задания отклоняются при разборе")
    void invalidEffects() {
        // подготовка
        YAMLMapper yaml = new YAMLMapper();

        // вызов и проверка
        assertThatThrownBy(() -> EffectParser.effects(yaml.readTree("- teleport: 3"), "тест"))
                .isInstanceOf(CatalogException.class)
                .hasMessageContaining("неизвестный вид эффекта teleport");
        assertThatThrownBy(() -> EffectParser.effects(yaml.readTree("- resources: { FIRE: 2 }"), "тест"))
                .isInstanceOf(CatalogException.class)
                .hasMessageContaining("стихии даёт общее правило победы");
    }
}
