package com.primalapp.model.campaign

/**
 * Дополнения игры. Какие есть у игрока — настройка (сервер: аккаунт, офлайн — приложение): задания отключённых
 * дополнений не показываются, а условие «есть дополнение» проверяется по дополнениям владельца кампании.
 */
enum class Expansion(val displayName: String, val quests: IntRange) {
    NIGHTMARE("Кошмар", 31..35),
    FEATHER("Перо", 36..40),
    POISON("Яд", 41..45),
    ICE("Лёд", 46..50);

    companion object {
        /** Дополнение задания по номеру; `null` — базовая игра (1–30). */
        fun ofQuest(number: Int): Expansion? = entries.firstOrNull { number in it.quests }
    }
}
