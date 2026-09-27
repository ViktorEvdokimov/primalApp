package com.primalapp.domain

/**
 * Сравнение названий достижений (задача 42.1).
 *
 * Одно и то же достижение записано по-разному в каталоге заданий, каталоге глав и при ручном
 * вводе («Яд пазиса» / «Яд Пазиса», «Копье» / «Копьё»), поэтому названия сравниваются без учёта
 * регистра, различия «ё/е» и лишних пробелов.
 */
fun normalizeAchievementName(name: String): String =
    name.trim()
        .replace(Regex("\\s+"), " ")
        .lowercase()
        .replace('ё', 'е')

fun achievementMatches(first: String, second: String): Boolean =
    normalizeAchievementName(first) == normalizeAchievementName(second)

fun Iterable<String>.containsAchievement(name: String): Boolean =
    any { achievementMatches(it, name) }
