package com.primal.campaign;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.primal.access.AccessService.CampaignAccess;
import com.primal.campaign.CampaignSheetDto.Actor;
import com.primal.common.api.ApiNullable;
import com.primal.rules.model.Element;
import com.primal.rules.model.Expansion;
import com.primal.rules.model.HunterClass;
import com.primal.rules.model.SkillBranch;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Снимок кампании для мобильного приложения ({@code doc/api.md} §11): всё состояние листа одним документом.
 * Приложение работает и без сети, считает награды само и отправляет снимок целиком с {@code expectedVersion}.
 */
public final class CampaignSnapshotDto {

    private CampaignSnapshotDto() {
    }

    public record SnapshotSkill(SkillBranch branch, int tier) {
    }

    /** Карта инвентаря; {@code id} — серверный, у новой карты приложения — {@code null}. */
    public record SnapshotItem(@ApiNullable Long id, HunterItem.Kind kind, String name, @ApiNullable Integer level,
                               @ApiNullable Element element, @ApiNullable String source) {
    }

    /** Охотник отряда; охотники сопоставляются по классу, он в кампании уникален. */
    public record SnapshotHunter(@ApiNullable Long id, @JsonProperty("class") HunterClass hunterClass, String playerName,
                                 int position, List<SnapshotSkill> skills, Map<String, Integer> resources,
                                 List<SnapshotItem> items) {
    }

    public record SnapshotQuest(int number, CampaignQuest.Status status, int openedInChapter,
                                @ApiNullable Integer closedInChapter) {
    }

    /** {@code code} — у достижения каталога; своё достижение, введённое вручную, кода не имеет. */
    public record SnapshotAchievement(@ApiNullable Long id, @ApiNullable String code, String name,
                                      CampaignAchievement.Source source, int grantedInChapter) {
    }

    /** Трофей: босс и глава победы; {@code battleId} — бой, который его дал. */
    public record SnapshotTrophy(String bossCode, int chapter, @ApiNullable UUID battleId) {
    }

    /**
     * Бой кампании — как в истории боёв ({@code doc/api.md} §6.3). Перечисления строками: {@code purpose} —
     * PROLOGUE | QUEST | FREE | FINAL, {@code status} — IN_PROGRESS | APPLIED | DISMISSED | ABANDONED,
     * {@code result} — VICTORY | DEFEAT, {@code defeatReason} — ROUNDS | SURRENDER. Кто начал и кто отправил итог —
     * только в ответе сервера.
     */
    public record SnapshotBattle(UUID id, String purpose, @ApiNullable Integer questNumber, @ApiNullable String bossCode,
                                 int difficulty, int chapter, int progressSeq, String status, @ApiNullable String result,
                                 @ApiNullable String defeatReason, @ApiNullable Integer roundsPlayed,
                                 @ApiNullable Actor startedBy, Instant startedAt, @ApiNullable Actor submittedBy,
                                 @ApiNullable Instant submittedAt, @ApiNullable Instant finishedAt) {
    }

    /** Состояние кампании: всё, что приложение может изменить. */
    public record SnapshotState(String name, int chapter, Campaign.Status status, int forgeLevel, int labLevel,
                                @ApiNullable String finalBossCode, int progressSeq, String notes,
                                List<SnapshotHunter> hunters, List<SnapshotQuest> quests,
                                List<SnapshotAchievement> achievements, List<SnapshotTrophy> trophies,
                                List<SnapshotBattle> battles) {
    }

    /**
     * Снимок с метаданными сервера: версия (для {@code expectedVersion}), доступ, владелец и его дополнения —
     * условие «есть дополнение» проверяется по дополнениям владельца (qa № 138).
     */
    public record CampaignSnapshot(long id, int version, CampaignAccess access, String ownerName,
                                   List<Expansion> ownerExpansions, Instant createdAt, Instant updatedAt,
                                   SnapshotState state) {
    }

    /** Запись снимка: {@code expectedVersion} — версия, на которой основано состояние приложения. */
    public record SnapshotUpdate(int expectedVersion, SnapshotState state) {
    }

    /** Кампания, созданная в приложении без сети, — на сервер. */
    public record SnapshotImport(SnapshotState state) {
    }
}
