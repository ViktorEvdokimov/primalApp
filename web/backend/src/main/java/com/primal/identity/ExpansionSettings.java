package com.primal.identity;

import com.primal.rules.model.Expansion;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Дополнения игрока (qa № 138) — настройка аккаунта, по умолчанию все. Задания отключённых дополнений сайт этому
 * игроку не показывает; условие «есть дополнение» в наградах проверяется по дополнениям владельца кампании.
 */
@Service
public class ExpansionSettings {

    private final JdbcTemplate jdbc;

    ExpansionSettings(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Включённые дополнения аккаунта. */
    public Set<Expansion> enabled(long userId) {
        Set<Expansion> enabled = EnumSet.allOf(Expansion.class);
        jdbc.queryForList("select expansion from user_hidden_expansion where user_id = ?", String.class, userId)
                .forEach(code -> enabled.remove(Expansion.valueOf(code)));
        return enabled;
    }

    /** Новый набор включённых дополнений; остальные отключаются. */
    @Transactional
    public Set<Expansion> set(long userId, Set<Expansion> enabled) {
        jdbc.update("delete from user_hidden_expansion where user_id = ?", userId);
        for (Expansion expansion : Expansion.values()) {
            if (!enabled.contains(expansion)) {
                jdbc.update("insert into user_hidden_expansion (user_id, expansion) values (?, ?)", userId, expansion.name());
            }
        }
        return enabled(userId);
    }
}
