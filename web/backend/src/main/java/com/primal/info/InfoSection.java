package com.primal.info;

/** Разделы «Инфо» (qa № 142) в порядке меню. */
public enum InfoSection {
    KEYWORDS("Ключевые слова", true),
    /** В правилах у символа реакции нет названия — только картинка и описание (qa № 143). */
    REACTIONS("Символы реакций монстров", false),
    TOKENS("Жетоны окружения", true);

    private final String title;
    private final boolean titled;

    InfoSection(String title, boolean titled) {
        this.title = title;
        this.titled = titled;
    }

    public String title() {
        return title;
    }

    /** У статей раздела есть название. */
    public boolean titled() {
        return titled;
    }
}
