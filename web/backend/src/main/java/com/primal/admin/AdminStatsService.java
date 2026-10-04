package com.primal.admin;

import com.primal.identity.Admins;
import com.primal.identity.PrimalPrincipal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Статистика для администратора (qa № 140): учётные записи, кампании и сыгранные бои кампаний — всего, за 30 и за
 * 7 дней. Экспедиция идёт только в браузере и на сервер не попадает — её бои здесь не считаются.
 */
@Service
class AdminStatsService {

    /** Сколько всего и сколько появилось за последние 30 и 7 дней. */
    record Counter(long total, long last30Days, long last7Days) {
    }

    /** Кампании: {@code active} — идут (в том числе ждут перехода главы), {@code completed} — пройдены. */
    record CampaignStats(Counter created, long active, long completed) {
    }

    /**
     * Бои кампаний: {@code played} — с результатом (принятым или отклонённым), по дате окончания боя;
     * {@code inProgress} — начаты и ещё не завершены.
     */
    record BattleStats(Counter played, long victories, long defeats, long inProgress) {
    }

    record AdminStats(Counter accounts, CampaignStats campaigns, BattleStats battles, Instant generatedAt) {
    }

    private final Admins admins;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    AdminStatsService(Admins admins, JdbcTemplate jdbc, Clock clock) {
        this.admins = admins;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    AdminStats stats(PrimalPrincipal principal) {
        admins.require(principal);
        Instant now = clock.instant();
        Timestamp month = Timestamp.from(now.minus(Duration.ofDays(30)));
        Timestamp week = Timestamp.from(now.minus(Duration.ofDays(7)));

        Counter accounts = counter("select count(*), count(*) filter (where created_at >= ?), "
                + "count(*) filter (where created_at >= ?) from app_user", month, week);
        Counter campaigns = counter("select count(*), count(*) filter (where created_at >= ?), "
                + "count(*) filter (where created_at >= ?) from campaign", month, week);
        long completed = count("select count(*) from campaign where status = 'COMPLETED'");
        Counter played = counter("select count(*), count(*) filter (where coalesce(finished_at, submitted_at) >= ?), "
                + "count(*) filter (where coalesce(finished_at, submitted_at) >= ?) from campaign_battle "
                + "where result is not null", month, week);
        return new AdminStats(
                accounts,
                new CampaignStats(campaigns, campaigns.total() - completed, completed),
                new BattleStats(played,
                        count("select count(*) from campaign_battle where result = 'VICTORY'"),
                        count("select count(*) from campaign_battle where result = 'DEFEAT'"),
                        count("select count(*) from campaign_battle where status = 'IN_PROGRESS'")),
                now);
    }

    private Counter counter(String sql, Timestamp month, Timestamp week) {
        return jdbc.queryForObject(sql, (rs, row) -> new Counter(rs.getLong(1), rs.getLong(2), rs.getLong(3)), month, week);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
