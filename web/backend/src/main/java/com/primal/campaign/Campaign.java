package com.primal.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Кампания. {@code version} растёт при любом изменении кампании — и её строки, и охотников, навыков,
 * ресурсов, заданий, достижений: сервисы вызывают {@link #touch(Instant)} ({@code doc/api.md} §1).
 */
@Entity
@Table(name = "campaign")
public class Campaign {

    public enum Status { ACTIVE, CHAPTER_TRANSITION, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private long ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private short chapter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "forge_level", nullable = false)
    private short forgeLevel = 1;

    @Column(name = "lab_level", nullable = false)
    private short labLevel = 1;

    @Column(name = "final_boss_code", length = 32)
    private String finalBossCode;

    @Column(name = "progress_seq", nullable = false)
    private int progressSeq;

    @Column(nullable = false, columnDefinition = "text")
    private String notes = "";

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Campaign() {
    }

    Campaign(long ownerId, String name, Instant now) {
        this.ownerId = ownerId;
        this.name = name;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Кампания изменилась: растут {@code updated_at} и версия (JPA увеличивает её при сохранении). */
    void touch(Instant now) {
        updatedAt = now;
    }

    void rename(String name) {
        this.name = name;
    }

    void setNotes(String notes) {
        this.notes = notes;
    }

    /** Ручная правка главы (исправление ошибки): растёт {@code progress_seq} — бои, начатые раньше, устаревают. */
    void changeChapter(int chapter) {
        this.chapter = (short) chapter;
        progressSeq++;
    }

    /** Кузня и лаборатория улучшаются переходами глав, не выше 3. */
    void forgeLevelUp() {
        forgeLevel = (short) Math.min(3, forgeLevel + 1);
    }

    void labLevelUp() {
        labLevel = (short) Math.min(3, labLevel + 1);
    }

    /**
     * Первая принятая победа закрывает главу: кампания ждёт перехода, растёт {@code progress_seq} — результаты
     * остальных боёв этой главы больше не принимаются.
     */
    void closeChapter() {
        status = Status.CHAPTER_TRANSITION;
        progressSeq++;
    }

    /** Переход принят: новая глава, бои снова можно начинать. */
    void enterChapter(int chapter) {
        this.chapter = (short) chapter;
        status = Status.ACTIVE;
        progressSeq++;
    }

    /** Переход отклонён («Отклонить» наград главы, как в app): глава прежняя, после следующей победы — снова. */
    void stayInChapter() {
        status = Status.ACTIVE;
    }

    /** Победа в финальном бою: кампания пройдена. */
    void finish() {
        status = Status.COMPLETED;
        progressSeq++;
    }

    /** Следующий бой — только с этим боссом (глава 11). */
    void setFinalBoss(String bossCode) {
        this.finalBossCode = bossCode;
    }

    public Long getId() {
        return id;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public int getChapter() {
        return chapter;
    }

    public Status getStatus() {
        return status;
    }

    public int getForgeLevel() {
        return forgeLevel;
    }

    public int getLabLevel() {
        return labLevel;
    }

    public String getFinalBossCode() {
        return finalBossCode;
    }

    public int getProgressSeq() {
        return progressSeq;
    }

    public String getNotes() {
        return notes;
    }

    public int getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
