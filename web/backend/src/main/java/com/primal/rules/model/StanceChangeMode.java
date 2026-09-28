package com.primal.rules.model;

/** Когда монстр переходит на следующую стойку (doc/data-model.md §1). */
public enum StanceChangeMode {
    /** Здоровье опустилось до порога. */
    HEALTH,
    /** По кнопке «Сменить стойку». */
    ON_DEMAND,
    /** Последняя стойка — смены нет. */
    FINAL
}
