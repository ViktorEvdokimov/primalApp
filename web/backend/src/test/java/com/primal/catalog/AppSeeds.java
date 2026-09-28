package com.primal.catalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Строки сидов мобильного приложения ({@code TASK_INFO_ROWS}, {@code CHAPTER_INFO_ROWS}) — эталон для проверки
 * переноса каталога. Пусто, если исходников app рядом с {@code web/} нет.
 */
final class AppSeeds {

    private static final Path DATABASE = Path.of("../../app/shared/src/commonMain/kotlin/com/primalapp/database");
    private static final Pattern ROW = Pattern.compile("arrayOf\\((.*?)\\n\\s*\\)", Pattern.DOTALL);
    private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    private AppSeeds() {
    }

    static Optional<List<List<String>>> taskRows() {
        return rows("TaskInfoSeed.kt", "TASK_INFO_ROWS");
    }

    static Optional<List<List<String>>> chapterRows() {
        return rows("ChapterInfoSeed.kt", "CHAPTER_INFO_ROWS");
    }

    /** Колонка строки или пустая строка, если колонки нет (последние колонки появились в поздних версиях). */
    static String column(List<String> row, int index) {
        return index < row.size() ? row.get(index) : "";
    }

    private static Optional<List<List<String>>> rows(String file, String listName) {
        Path path = DATABASE.resolve(file);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            String body = text.substring(text.indexOf("private val " + listName + " = listOf("));
            body = body.substring(0, body.indexOf("\n)\n") + 2);
            List<List<String>> rows = new ArrayList<>();
            Matcher row = ROW.matcher(body);
            while (row.find()) {
                List<String> values = new ArrayList<>();
                Matcher string = STRING.matcher(row.group(1));
                while (string.find()) {
                    values.add(string.group(1));
                }
                rows.add(values);
            }
            return Optional.of(rows);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
