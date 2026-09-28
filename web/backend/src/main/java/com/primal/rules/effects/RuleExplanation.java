package com.primal.rules.effects;

/**
 * Условное правило для окна наград: формулировка и результат проверки для этой кампании
 * («Если текущая глава 1 или 2, то добавить задание 4, иначе добавить задание 6» — «Добавлено задание 4.»).
 */
public record RuleExplanation(String description, String result) {
}
