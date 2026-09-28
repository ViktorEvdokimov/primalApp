package com.primal.rules.model;

/** Уровень враждебности монстров по главе книги кампании (правила, «Уровень враждебности монстров»). */
public final class Difficulty {

    public static final int MAX = 3;

    private Difficulty() {
    }

    /** Глава 0 (пролог) → 0, главы 1–3 → 1, 4–7 → 2, 8–11 → 3. */
    public static int forChapter(int chapter) {
        if (chapter <= 0) {
            return 0;
        }
        if (chapter <= 3) {
            return 1;
        }
        return chapter <= 7 ? 2 : 3;
    }
}
