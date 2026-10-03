package com.primal.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.primal.rules.model.Element;
import com.primal.rules.model.ForgeSlot;
import com.primal.rules.model.HunterClass;
import com.primal.rules.model.Material;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

@DisplayName("Каталог: планшеты кузни")
class ForgeCatalogTest {

    private static final Catalog CATALOG = CatalogLoader.load();

    private static ForgeItemDef item(Element element, String name) {
        return CATALOG.forge(element).stream().filter(item -> item.name().equals(name)).findFirst().orElseThrow();
    }

    @Nested
    @DisplayName("Данные")
    class Data {

        @Test
        @DisplayName("9 стихий по 12 предметов; коды — стихия и место на планшете")
        void boards() {
            // вызов и проверка
            for (Element element : Element.values()) {
                List<ForgeItemDef> board = CATALOG.forge(element);
                assertThat(board).as(element.name()).hasSize(12);
                assertThat(board.getFirst().code()).isEqualTo(element.name() + "_01");
                assertThat(board.getLast().code()).isEqualTo(element.name() + "_12");
            }
            assertThat(CATALOG.forge()).hasSize(108);
        }

        @Test
        @DisplayName("кузня огня 1-го уровня — как согласовано: предмет, слот, материи")
        void fireLevel1() {
            // подготовка: таблица, подтверждённая по планшету (qa.md № 131)
            Map<String, Map<Material, Integer>> expected = new LinkedHashMap<>();
            expected.put("Язык пламени", Map.of(Material.BONES, 1, Material.BLOOD, 1));
            expected.put("Лук-испепелитель", Map.of(Material.SCALES, 1, Material.BLOOD, 1));
            expected.put("Молот пламени", Map.of(Material.BLOOD, 2));
            expected.put("Вулканический щит", Map.of(Material.BLOOD, 1, Material.IRIDIA, 1));
            expected.put("Крылья дракона", Map.of(Material.SCALES, 1, Material.BLOOD, 1));
            expected.put("Игнис", Map.of(Material.BLOOD, 1, Material.IRIDIA, 1));
            expected.put("Магматическое копьё", Map.of(Material.SCALES, 1, Material.BLOOD, 1));
            expected.put("Лавовый барабан", Map.of(Material.SCALES, 2));
            expected.put("Чешуйчатый шлем", Map.of(Material.SCALES, 1, Material.BLOOD, 1));
            expected.put("Чешуйчатый доспех", Map.of(Material.SCALES, 1, Material.BLOOD, 1));
            expected.put("Перчатка Волтьяра", Map.of(Material.SCALES, 1, Material.BLOOD, 1));
            expected.put("Лавовый щит", Map.of(Material.SCALES, 2));

            // вызов
            List<ForgeItemDef> board = CATALOG.forge(Element.FIRE);

            // проверка
            assertThat(board).extracting(ForgeItemDef::name).containsExactlyElementsOf(expected.keySet());
            board.forEach(item -> assertThat(item.cost(1)).as(item.name()).isEqualTo(expected.get(item.name())));
            assertThat(board).extracting(ForgeItemDef::slot).containsExactly(
                    ForgeSlot.GREATSWORD, ForgeSlot.GREATBOW, ForgeSlot.HAMMER, ForgeSlot.SWORD_AND_SHIELD,
                    ForgeSlot.DUAL_BLADES, ForgeSlot.GUN, ForgeSlot.SPEAR, ForgeSlot.DRUM,
                    ForgeSlot.HELMET, ForgeSlot.ARMOR, ForgeSlot.ITEM, ForgeSlot.ITEM);
        }

        @Test
        @DisplayName("примеры из правил: «Чешуйчатый доспех» 1-го уровня и улучшение «Языка пламени» до 2-го")
        void rulebookExamples() {
            // вызов и проверка: 1 чешуя и 1 кровь в обоих примерах
            assertThat(item(Element.FIRE, "Чешуйчатый доспех").cost(1))
                    .isEqualTo(Map.of(Material.SCALES, 1, Material.BLOOD, 1));
            assertThat(item(Element.FIRE, "Язык пламени").cost(2))
                    .isEqualTo(Map.of(Material.SCALES, 1, Material.BLOOD, 1));
        }

        @Test
        @DisplayName("оружие привязано к классам: Кара — парные клинки, Хелерен — пушка, Зарайа — копьё, Друск — барабан")
        void weaponClasses() {
            // вызов и проверка
            assertThat(ForgeSlot.weaponOf(HunterClass.KARA)).isEqualTo(ForgeSlot.DUAL_BLADES);
            assertThat(ForgeSlot.weaponOf(HunterClass.HELEREN)).isEqualTo(ForgeSlot.GUN);
            assertThat(ForgeSlot.weaponOf(HunterClass.ZARAIA)).isEqualTo(ForgeSlot.SPEAR);
            assertThat(ForgeSlot.weaponOf(HunterClass.DRUSK)).isEqualTo(ForgeSlot.DRUM);
            assertThat(ForgeSlot.GREATBOW.availableTo(HunterClass.DAREON)).isFalse();
            assertThat(ForgeSlot.HELMET.availableTo(HunterClass.DAREON)).isTrue();
        }

        @Test
        @DisplayName("у каждого предмета на каждом уровне — 2 материи")
        void twoMaterials() {
            // вызов и проверка
            CATALOG.forge().forEach(item -> {
                for (int level = 1; level <= ForgeItemDef.LEVELS; level++) {
                    assertThat(item.cost(level).values().stream().mapToInt(Integer::intValue).sum())
                            .as(item.code() + ", уровень " + level).isEqualTo(2);
                }
            });
        }
    }

    @Nested
    @DisplayName("Ошибки в данных")
    class Errors {

        private final YAMLMapper yaml = new YAMLMapper();

        private JsonNode board(String items) {
            return yaml.readTree("- element: FIRE\n  items:\n" + items);
        }

        private static String line(String name, String slot, String cost) {
            return "    - { name: " + name + ", slot: " + slot + ", cost: " + cost + " }\n";
        }

        private static String fullBoard(String lastCost) {
            StringBuilder items = new StringBuilder();
            String cost = "[[BLOOD, BLOOD], [BLOOD, BLOOD], [BLOOD, BLOOD]]";
            for (String slot : List.of("GREATSWORD", "GREATBOW", "HAMMER", "SWORD_AND_SHIELD", "DUAL_BLADES", "GUN",
                    "SPEAR", "DRUM", "HELMET", "ARMOR", "ITEM")) {
                items.append(line("П" + slot, slot, cost));
            }
            items.append(line("Последний", "ITEM", lastCost));
            return items.toString();
        }

        @Test
        @DisplayName("не 2 материи на уровне — ошибка с названием предмета и уровнем")
        void wrongMaterials() {
            // вызов и проверка
            assertThatThrownBy(() -> CatalogLoader.parseForge(board(fullBoard("[[BLOOD], [BLOOD, BLOOD], [BLOOD, BLOOD]]"))))
                    .isInstanceOf(CatalogException.class)
                    .hasMessageContaining("Последний, уровень 1: на планшете 2 материи");
        }

        @Test
        @DisplayName("неполный планшет — ошибка состава")
        void incompleteBoard() {
            // вызов и проверка
            assertThatThrownBy(() -> CatalogLoader.parseForge(board(line("Меч", "GREATSWORD",
                    "[[BLOOD, BLOOD], [BLOOD, BLOOD], [BLOOD, BLOOD]]"))))
                    .isInstanceOf(CatalogException.class)
                    .hasMessageContaining("forge.yaml, FIRE: на планшете должны быть");
        }

        @Test
        @DisplayName("неизвестная материя — ошибка")
        void unknownMaterial() {
            // вызов и проверка
            assertThatThrownBy(() -> CatalogLoader.parseForge(board(fullBoard("[[GOLD, BLOOD], [BLOOD, BLOOD], [BLOOD, BLOOD]]"))))
                    .isInstanceOf(CatalogException.class)
                    .hasMessageContaining("неизвестное значение GOLD");
        }

        @Test
        @DisplayName("не все стихии — ошибка")
        void missingElements() {
            // вызов и проверка
            assertThatThrownBy(() -> CatalogLoader.parseForge(board(fullBoard("[[BLOOD, BLOOD], [BLOOD, BLOOD], [BLOOD, BLOOD]]"))))
                    .isInstanceOf(CatalogException.class)
                    .hasMessageContaining("нужны планшеты всех 9 стихий, а есть 1");
        }
    }
}
