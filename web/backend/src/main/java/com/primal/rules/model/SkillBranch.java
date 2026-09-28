package com.primal.rules.model;

/** Ветви древа навыков охотника: А–Д, в каждой две ступени. */
public enum SkillBranch {
    A("А"),
    B("Б"),
    V("В"),
    G("Г"),
    D("Д");

    private final String letter;

    SkillBranch(String letter) {
        this.letter = letter;
    }

    public String letter() {
        return letter;
    }
}
