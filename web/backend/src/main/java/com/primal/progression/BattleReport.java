package com.primal.progression;

import com.primal.progression.CampaignBattle.DefeatReason;
import com.primal.progression.CampaignBattle.Purpose;
import com.primal.progression.CampaignBattle.Result;
import java.time.Instant;

/**
 * Результат боя от браузера ({@code doc/api.md} §7.1): поля старта (снимок подготовки) и итог. {@code purpose}
 * выводится из снимка: задание → {@code QUEST}, глава 0 → {@code PROLOGUE}, глава 11 → {@code FINAL}, иначе
 * {@code FREE}.
 */
public record BattleReport(
        Purpose purpose,
        Integer questNumber,
        String bossCode,
        int difficulty,
        int chapter,
        int progressSeq,
        Result result,
        DefeatReason defeatReason,
        Integer roundsPlayed,
        Instant startedAt,
        Instant finishedAt) {

    /** Финальная глава кампании: бой только с финальным боссом. */
    public static final int FINAL_CHAPTER = 11;

    public static Purpose purposeOf(Integer questNumber, int chapter) {
        if (questNumber != null) {
            return Purpose.QUEST;
        }
        if (chapter == 0) {
            return Purpose.PROLOGUE;
        }
        return chapter == FINAL_CHAPTER ? Purpose.FINAL : Purpose.FREE;
    }

    public boolean victory() {
        return result == Result.VICTORY;
    }
}
