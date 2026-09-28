package com.primal.rules.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Паритет с {@code AchievementNamesTest.kt} (задача 42.1 app). */
@DisplayName("Названия достижений")
class AchievementNamesTest {

    @Test
    @DisplayName("нормализация убирает регистр, ё и лишние пробелы")
    void normalize() {
        // вызов и проверка
        assertThat(AchievementNames.normalize("  Копьё   Драконоборца ")).isEqualTo("копье драконоборца");
    }

    @Test
    @DisplayName("названия сравниваются без учёта регистра")
    void matches() {
        // вызов и проверка
        assertThat(AchievementNames.matches("Яд пазиса", "Яд Пазиса")).isTrue();
        assertThat(AchievementNames.matches("Яд Пазиса", "Три копья")).isFalse();
    }

    @Test
    @DisplayName("достижение находится в другом написании")
    void contains() {
        // вызов и проверка
        assertThat(AchievementNames.contains(List.of("Копье драконоборца", "Затишье"), "копьё  драконоборца")).isTrue();
        assertThat(AchievementNames.contains(List.of("Затишье"), "Гербарий")).isFalse();
    }
}
