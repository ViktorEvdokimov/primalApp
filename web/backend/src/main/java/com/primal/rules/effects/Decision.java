package com.primal.rules.effects;

import java.util.List;

/** Решение главы: вопрос и варианты ответа, у варианта могут быть свои эффекты (глава 7). */
public record Decision(String code, String question, List<Option> options) {

    public Decision {
        options = List.copyOf(options);
    }

    public record Option(String code, String label, List<Effect> effects) {
        public Option {
            effects = List.copyOf(effects);
        }
    }
}
