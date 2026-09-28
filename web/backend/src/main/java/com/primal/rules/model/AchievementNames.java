package com.primal.rules.model;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Сравнение названий достижений — перенос {@code AchievementNames.kt} (задача 42.1 app): одно и то же
 * достижение пишут по-разному («Яд пазиса» / «Яд Пазиса», «Копье» / «Копьё»), поэтому названия
 * сравниваются без учёта регистра, различия «ё/е» и лишних пробелов.
 */
public final class AchievementNames {

    private static final Pattern SPACES = Pattern.compile("\\s+");

    private AchievementNames() {
    }

    public static String normalize(String name) {
        return SPACES.matcher(name.strip()).replaceAll(" ").toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    public static boolean matches(String first, String second) {
        return normalize(first).equals(normalize(second));
    }

    public static boolean contains(Collection<String> names, String name) {
        return names.stream().anyMatch(existing -> matches(existing, name));
    }
}
