package com.primal.info;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.primal.info.RulesKeywordParser.ParsedEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Разбор раздела «Ключевые слова» из Markdown правил")
class RulesKeywordParserTest {

    /** Пример в той же вёрстке, что файл правил (текст свой): жирные названия, блоки-колонки, переносы. */
    private static final String SAMPLE = """
            # Правила
            Вступление.
            ##### Ключевые слова

            **Первое слово**
            Описание первого слова, пере-
            несённое на строку.
            **Примечание.** Отдельный абзац.
            (См. также **«Второе слово»** .)

            **Второе слово / жетон
            второго слова ( )**
            Текст второго слова.

            ```
            Третье слово
            Описание в колонке.
            (См. также «Четвёртое слово» .)
            ```
            ```
            Продолжение абзаца из колонки, а не
            название: следующая строка со строчной.
            ```
            ```
            Пятое / жетон
            пятого ( )
            Перенесённое название в колонке.
            ```
            Четвёртое слово
            Название без оформления — узнаётся по ссылке.
            #### Алфавитный указатель
            Не входит в раздел.
            """;

    @Test
    @DisplayName("названия: жирные (и перенесённые), первая строка колонки, по ссылке «См. также»")
    void titles() {
        // вызов
        List<ParsedEntry> entries = RulesKeywordParser.parse(SAMPLE);

        // проверка
        assertThat(entries).extracting(ParsedEntry::title).containsExactly(
                "Первое слово", "Второе слово / жетон второго слова ( )", "Третье слово", "Пятое / жетон пятого ( )",
                "Четвёртое слово");
    }

    @Test
    @DisplayName("текст: переносы склеены, абзацы — «Примечание» и «См. также»; продолжение колонки — в той же статье")
    void body() {
        // вызов
        List<ParsedEntry> entries = RulesKeywordParser.parse(SAMPLE);

        // проверка
        assertThat(entries.getFirst().body()).isEqualTo("""
                Описание первого слова, перенесённое на строку.

                **Примечание.** Отдельный абзац.

                (См. также **«Второе слово»** .)""");
        assertThat(entries.get(2).body()).contains("Продолжение абзаца из колонки")
                .doesNotContain("Алфавитный");
    }

    @Test
    @DisplayName("нет раздела — пустой список; разделы после «Ключевых слов» не попадают")
    void noSection() {
        // вызов и проверка
        assertThat(RulesKeywordParser.parse("# Правила\nТекст")).isEmpty();
        assertThat(RulesKeywordParser.parse(SAMPLE)).noneMatch(entry -> entry.body().contains("Не входит"));
    }

    @Test
    @DisplayName("файл правил из app (если есть рядом): около 90 статей, все «См. также» находят статью")
    void rulesFile() throws IOException {
        // подготовка
        Path file = Path.of("../../app/Primal. Пробуждение.md");
        assumeTrue(Files.exists(file), "файл правил рядом с web/");
        String text = Files.readString(file, StandardCharsets.UTF_8);

        // вызов
        List<ParsedEntry> entries = RulesKeywordParser.parse(text);

        // проверка: названия короткие, ссылки разрешаются так же, как на сайте (без «( )», по началу названия)
        assertThat(entries).hasSizeGreaterThanOrEqualTo(90);
        assertThat(entries).allMatch(entry -> entry.title().length() <= 60, "название не длиннее 60 символов");
        Set<String> keys = entries.stream().map(entry -> key(entry.title())).collect(Collectors.toSet());
        int references = 0;
        for (ParsedEntry entry : entries) {
            Matcher seeAlso = RulesKeywordParser.SEE_ALSO.matcher(entry.body());
            while (seeAlso.find()) {
                Matcher quoted = QUOTED.matcher(seeAlso.group(1));
                while (quoted.find()) {
                    references++;
                    String reference = key(quoted.group(1));
                    assertThat(keys).as("ссылка «%s» в «%s»", reference, entry.title())
                            .anyMatch(title -> title.equals(reference)
                                    || title.split(" / | \\(")[0].strip().equals(reference) || title.startsWith(reference));
                }
            }
        }
        assertThat(references).isGreaterThan(30);
    }

    private static final Pattern QUOTED = Pattern.compile("«([^»]+)»");

    private static String key(String title) {
        return RulesKeywordParser.normalize(title).replaceAll("\\(\\s*[^\\p{L}\\p{N}(]*\\s*\\)", "").replaceAll("\\s+", " ")
                .strip();
    }
}
