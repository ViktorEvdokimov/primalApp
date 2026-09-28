package com.primal.progression;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Бой кампании: отметка о начале (задача 5.1), затем итог (задача 5.2). Идентификатор создаёт браузер при
 * старте боя, поэтому повтор запроса не создаёт второй записи ({@code doc/data-model.md} §3.4).
 */
@Entity
@Table(name = "campaign_battle")
public class CampaignBattle {

    /** Зачем бой: пролог (глава 0), задание, бой без задания, финальный бой главы 11. */
    public enum Purpose { PROLOGUE, QUEST, FREE, FINAL }

    /** Идёт; итог принят; итог отклонён; бой брошен (отметка снята). */
    public enum Status { IN_PROGRESS, APPLIED, DISMISSED, ABANDONED }

    public enum Result { VICTORY, DEFEAT }

    /** Поражение: закончились раунды или отряд сдался. */
    public enum DefeatReason { ROUNDS, SURRENDER }

    @Id
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private long campaignId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Purpose purpose;

    @Column(name = "quest_number")
    private Short questNumber;

    @Column(name = "boss_code", length = 32)
    private String bossCode;

    @Column(nullable = false)
    private short difficulty;

    @Column(nullable = false)
    private short chapter;

    @Column(name = "progress_seq", nullable = false)
    private int progressSeq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.IN_PROGRESS;

    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private Result result;

    @Enumerated(EnumType.STRING)
    @Column(name = "defeat_reason", length = 12)
    private DefeatReason defeatReason;

    @Column(name = "rounds_played")
    private Short roundsPlayed;

    /** Награды, исправленные через «Редактировать» ({@code overrides} запроса), — как были применены. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String overrides;

    @Column(name = "started_by_device_id")
    private UUID startedByDeviceId;

    @Column(name = "submitted_by_device_id")
    private UUID submittedByDeviceId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    protected CampaignBattle() {
    }

    /** Итог боя без отметки о начале (она не дошла до сервера): запись создаётся из полей результата. */
    CampaignBattle(UUID id, long campaignId, BattleReport report, UUID startedByDeviceId) {
        this.id = id;
        this.campaignId = campaignId;
        this.startedByDeviceId = startedByDeviceId;
        describe(report);
    }

    /**
     * Итог сохранён один раз: принят ({@code APPLIED}) или отклонён ({@code DISMISSED}). Поля старта берутся из
     * результата — они повторяют отметку о начале.
     */
    void finish(Status outcome, BattleReport report, UUID submittedBy, Instant now, String appliedOverrides) {
        describe(report);
        status = outcome;
        result = report.result();
        defeatReason = report.result() == Result.DEFEAT ? report.defeatReason() : null;
        roundsPlayed = report.roundsPlayed() == null ? null : report.roundsPlayed().shortValue();
        finishedAt = report.finishedAt();
        submittedByDeviceId = submittedBy;
        submittedAt = now;
        overrides = appliedOverrides;
    }

    boolean isFinished() {
        return status == Status.APPLIED || status == Status.DISMISSED;
    }

    private void describe(BattleReport report) {
        purpose = report.purpose();
        questNumber = report.purpose() == Purpose.QUEST ? report.questNumber().shortValue() : null;
        bossCode = report.bossCode();
        difficulty = (short) report.difficulty();
        chapter = (short) report.chapter();
        progressSeq = report.progressSeq();
        startedAt = report.startedAt();
    }

    /** Отметку снял участник кампании: бой больше не предупреждает других; итог по-прежнему можно отправить. */
    void abandon() {
        if (status == Status.IN_PROGRESS) {
            status = Status.ABANDONED;
        }
    }

    public UUID getId() {
        return id;
    }

    public long getCampaignId() {
        return campaignId;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public Integer getQuestNumber() {
        return questNumber == null ? null : questNumber.intValue();
    }

    public String getBossCode() {
        return bossCode;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public int getChapter() {
        return chapter;
    }

    public int getProgressSeq() {
        return progressSeq;
    }

    public Status getStatus() {
        return status;
    }

    public Result getResult() {
        return result;
    }

    public DefeatReason getDefeatReason() {
        return defeatReason;
    }

    public Integer getRoundsPlayed() {
        return roundsPlayed == null ? null : roundsPlayed.intValue();
    }

    public UUID getStartedByDeviceId() {
        return startedByDeviceId;
    }

    public UUID getSubmittedByDeviceId() {
        return submittedByDeviceId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
