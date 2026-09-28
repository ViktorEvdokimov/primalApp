package com.primal.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.primal.rules.model.Element;
import com.primal.rules.model.StanceChangeMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.yaml.YAMLMapper;

@DisplayName("Каталог: боссы и достижения")
class BossCatalogTest {

    private static final Catalog CATALOG = CatalogLoader.load();

    @Nested
    @DisplayName("Боссы")
    class Bosses {

        @Test
        @DisplayName("23 босса с уникальными кодами")
        void allBosses() {
            // вызов
            List<String> codes = CATALOG.bosses().stream().map(BossDef::code).toList();

            // проверка
            assertThat(codes).hasSize(23).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("Вираксен: стойки на уровнях враждебности 0–3, как в seed app")
        void viraxen() {
            // вызов
            BossDef viraxen = boss("VIRAXEN");

            // проверка
            assertThat(viraxen.element()).isEqualTo(Element.FIRE);
            assertThat(viraxen.stances(0)).containsExactly(
                    new StanceDef(1, 2, StanceChangeMode.HEALTH, 7),
                    new StanceDef(2, 3, StanceChangeMode.HEALTH, 3),
                    new StanceDef(3, 4, StanceChangeMode.FINAL, null));
            assertThat(viraxen.stances(3)).containsExactly(
                    new StanceDef(1, 18, StanceChangeMode.HEALTH, 7),
                    new StanceDef(2, 24, StanceChangeMode.HEALTH, 3),
                    new StanceDef(3, 30, StanceChangeMode.FINAL, null));
        }

        @Test
        @DisplayName("Иекорос: все стойки меняются по запросу")
        void iekorosOnDemand() {
            // вызов
            BossDef iekoros = boss("IEKOROS");

            // проверка
            iekoros.stances().values().forEach(stances -> assertThat(stances)
                    .extracting(StanceDef::changeMode)
                    .containsOnly(StanceChangeMode.ON_DEMAND));
        }

        @Test
        @DisplayName("Коровон: стойка II без порога раны и со сменой по запросу")
        void korovonStanceTwo() {
            // вызов
            StanceDef second = boss("KOROVON").stances(1).get(1);

            // проверка
            assertThat(second).isEqualTo(new StanceDef(2, null, StanceChangeMode.ON_DEMAND, null));
        }

        @Test
        @DisplayName("Кситерос: стойка II меняется по запросу")
        void xiterosStanceTwo() {
            // вызов
            StanceDef second = boss("XITEROS").stances(2).get(1);

            // проверка
            assertThat(second).isEqualTo(new StanceDef(2, 20, StanceChangeMode.ON_DEMAND, null));
        }

        @Test
        @DisplayName("Пробуждённый: без стихии, только уровень 3, пять стоек")
        void awakened() {
            // вызов
            BossDef awakened = boss("AWAKENED");

            // проверка
            assertThat(awakened.element()).isNull();
            assertThat(awakened.stances()).containsOnlyKeys(3);
            assertThat(awakened.stances(3)).extracting(StanceDef::toughnessPerHunter).containsExactly(30, 40, 50, 60, 60);
            assertThat(awakened.stances(3)).extracting(StanceDef::changeAtHealth).containsExactly(8, 6, 4, 2, null);
        }

        @Test
        @DisplayName("у каждого босса стойки I–III на каждом уровне, у всех, кроме Пробуждённого, — уровни 0–3")
        void everyBossHasThreeStancesPerDifficulty() {
            // вызов и проверка
            for (BossDef boss : CATALOG.bosses()) {
                boss.stances().values().forEach(stances -> assertThat(stances).as(boss.name()).hasSizeGreaterThanOrEqualTo(3));
                if (!boss.code().equals("AWAKENED")) {
                    assertThat(boss.stances()).as(boss.name()).containsOnlyKeys(0, 1, 2, 3);
                }
            }
        }

        @Test
        @DisplayName("порядок как в app: по названию стихии, затем по имени; Пробуждённый последним")
        void sortOrder() {
            // подготовка
            Comparator<BossDef> appOrder = Comparator
                    .comparing((BossDef b) -> b.element() == null)
                    .thenComparing(b -> b.element() == null ? "" : b.element().displayName().replace('ё', 'е'))
                    .thenComparing(b -> b.name().replace('ё', 'е'));

            // вызов
            List<BossDef> bySortOrder = CATALOG.bosses().stream().sorted(Comparator.comparingInt(BossDef::sortOrder)).toList();

            // проверка
            assertThat(bySortOrder).isSortedAccordingTo(appOrder);
            assertThat(bySortOrder.getLast().code()).isEqualTo("AWAKENED");
        }

        @Test
        @DisplayName("стойки всех боссов совпадают с seedBosses() мобильного приложения")
        void matchesAppSeed() throws IOException {
            // подготовка
            Path seed = Path.of("../../app/shared/src/commonMain/kotlin/com/primalapp/database/PrimalDatabase.kt");
            assumeTrue(Files.exists(seed), "исходники app рядом с web/");
            Map<String, Map<Integer, List<String>>> expected = parseAppSeed(Files.readString(seed, StandardCharsets.UTF_8));

            // вызов
            Map<String, Map<Integer, List<String>>> actual = new HashMap<>();
            for (BossDef boss : CATALOG.bosses()) {
                Map<Integer, List<String>> levels = new HashMap<>();
                boss.stances().forEach((level, stances) -> levels.put(level, stances.stream().map(BossCatalogTest::appNotation).toList()));
                actual.put(boss.name(), levels);
            }

            // проверка
            assertThat(actual).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Достижения")
    class Achievements {

        @Test
        @DisplayName("23 достижения с уникальными кодами и каноническими названиями (42.1)")
        void canonicalNames() {
            // вызов
            List<AchievementDef> achievements = CATALOG.achievements();

            // проверка
            assertThat(achievements).hasSize(23);
            assertThat(achievements).extracting(AchievementDef::code).doesNotHaveDuplicates();
            assertThat(achievements).contains(
                    new AchievementDef("GOLOS_VOLTYARA", "Голос Волтьяра"),
                    new AchievementDef("NAROD_ZOLOTYKH_GOR", "Народ Золотых гор"),
                    new AchievementDef("YAD_PAZISA", "Яд Пазиса"),
                    new AchievementDef("KOPE_DRAKONOBORTSA", "Копьё драконоборца"),
                    new AchievementDef("ZVEZDA_DRAKONA", "Звезда дракона"),
                    new AchievementDef("OLEDENENIE", "Оледенение"));
        }
    }

    @Nested
    @DisplayName("Ошибки в YAML")
    class Errors {

        @Test
        @DisplayName("неизвестный режим смены стойки останавливает загрузку с указанием места")
        void unknownChangeMode() {
            // подготовка
            var yaml = new YAMLMapper().readTree("""
                    - code: TEST
                      name: Тест
                      element: FIRE
                      stances:
                        1: [{t: 2, change: SOMETIMES}, {t: 3, change: 3}, {t: 4, change: FINAL}]
                    """);

            // вызов и проверка
            assertThatThrownBy(() -> CatalogLoader.parseBosses(yaml))
                    .isInstanceOf(CatalogException.class)
                    .hasMessageContaining("TEST, сложность 1, стойка 1");
        }

        @Test
        @DisplayName("меньше трёх стоек — ошибка")
        void tooFewStances() {
            // подготовка
            var yaml = new YAMLMapper().readTree("""
                    - code: TEST
                      name: Тест
                      stances:
                        0: [{t: 2, change: 7}, {t: 3, change: FINAL}]
                    """);

            // вызов и проверка
            assertThatThrownBy(() -> CatalogLoader.parseBosses(yaml))
                    .isInstanceOf(CatalogException.class)
                    .hasMessageContaining("стоек должно быть от 3 до 9");
        }
    }

    private static BossDef boss(String code) {
        return CATALOG.boss(code).orElseThrow();
    }

    /** Стойка в записи seed app: «dfw/hsc», где пусто — NULL, 0 — последняя стойка. */
    private static String appNotation(StanceDef stance) {
        String dfw = stance.toughnessPerHunter() == null ? "NULL" : String.valueOf(stance.toughnessPerHunter());
        String hsc = switch (stance.changeMode()) {
            case HEALTH -> String.valueOf(stance.changeAtHealth());
            case FINAL -> "0";
            case ON_DEMAND -> "NULL";
        };
        return dfw + "/" + hsc;
    }

    /** Разбор вызовов insert3…() и строки Пробуждённого из seedBosses() app. */
    private static Map<String, Map<Integer, List<String>>> parseAppSeed(String source) {
        String seed = source.substring(source.indexOf("fun seedBosses"), source.indexOf("@Database("));
        Map<String, Map<Integer, List<String>>> bosses = new HashMap<>();
        Matcher call = Pattern.compile("^\\s*(insert3\\w*)\\(\"([^\"]+)\", \"[A-Z]+\", ([^)]*)\\)", Pattern.MULTILINE).matcher(seed);
        while (call.find()) {
            List<String> args = List.of(call.group(3).split(",\\s*"));
            int level = Integer.parseInt(args.getFirst());
            List<String> v = args.subList(1, args.size());
            List<String> stances = switch (call.group(1)) {
                case "insert3" -> List.of(v.get(0) + "/" + v.get(1), v.get(2) + "/" + v.get(3), v.get(4) + "/" + v.get(5));
                case "insert3NullHsc" -> List.of(v.get(0) + "/NULL", v.get(1) + "/NULL", v.get(2) + "/NULL");
                case "insert3NullDfw" -> List.of(v.get(0) + "/" + v.get(1), "NULL/NULL", v.get(2) + "/" + v.get(3));
                case "insert3NullHsc2" -> List.of(v.get(0) + "/" + v.get(1), v.get(2) + "/NULL", v.get(3) + "/" + v.get(4));
                default -> throw new IllegalStateException(call.group(1));
            };
            bosses.computeIfAbsent(call.group(2), name -> new HashMap<>()).put(level, stances);
        }
        Matcher awakened = Pattern.compile("VALUES \\('Пробуждённый', NULL, (\\d+), ([^)]*)\\)").matcher(seed);
        assertThat(awakened.find()).isTrue();
        List<String> v = List.of(awakened.group(2).split(",\\s*"));
        List<String> stances = new ArrayList<>();
        for (int i = 0; i < v.size(); i += 2) {
            stances.add(v.get(i) + "/" + v.get(i + 1));
        }
        bosses.put("Пробуждённый", Map.of(Integer.parseInt(awakened.group(1)), stances));
        return bosses;
    }
}
