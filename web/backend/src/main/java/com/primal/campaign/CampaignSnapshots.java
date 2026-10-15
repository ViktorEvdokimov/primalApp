package com.primal.campaign;

import com.primal.access.AccessService;
import com.primal.access.AccessService.CampaignAccess;
import com.primal.campaign.CampaignSnapshotDto.CampaignSnapshot;
import com.primal.campaign.CampaignSnapshotDto.SnapshotAchievement;
import com.primal.campaign.CampaignSnapshotDto.SnapshotBattle;
import com.primal.campaign.CampaignSnapshotDto.SnapshotHunter;
import com.primal.campaign.CampaignSnapshotDto.SnapshotItem;
import com.primal.campaign.CampaignSnapshotDto.SnapshotQuest;
import com.primal.campaign.CampaignSnapshotDto.SnapshotSkill;
import com.primal.campaign.CampaignSnapshotDto.SnapshotState;
import com.primal.campaign.CampaignSnapshotDto.SnapshotTrophy;
import com.primal.catalog.AchievementDef;
import com.primal.catalog.CatalogService;
import com.primal.common.config.PrimalProperties;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.AccountService;
import com.primal.identity.ExpansionSettings;
import com.primal.identity.PrimalPrincipal;
import com.primal.identity.PrimalPrincipal.UserPrincipal;
import com.primal.rules.model.AchievementNames;
import com.primal.rules.model.HunterClass;
import com.primal.rules.model.ResourceCode;
import com.primal.rules.model.SkillTree.Skill;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Снимок кампании для мобильного приложения ({@code doc/api.md} §11): чтение, запись целиком с проверкой версии и
 * загрузка кампании, созданной в приложении без сети. Приложение само считает награды и переходы глав по тем же
 * правилам, а сервер проверяет только формат и ссылки на каталог.
 */
@Service
public class CampaignSnapshots {

    private static final int MAX_NOTES = 20_000;

    private final CampaignService campaigns;
    private final CampaignRepository repository;
    private final CampaignHunterRepository hunters;
    private final HunterSkillRepository skills;
    private final HunterResourceRepository resources;
    private final HunterItemRepository items;
    private final CampaignQuestRepository quests;
    private final CampaignAchievementRepository achievements;
    private final CampaignTrophyRepository trophies;
    private final SnapshotBattles battles;
    private final CatalogService catalog;
    private final AccessService access;
    private final AccountService accounts;
    private final ExpansionSettings expansions;
    private final PrimalProperties properties;
    private final Clock clock;

    CampaignSnapshots(CampaignService campaigns, CampaignRepository repository, CampaignHunterRepository hunters,
                      HunterSkillRepository skills, HunterResourceRepository resources, HunterItemRepository items,
                      CampaignQuestRepository quests, CampaignAchievementRepository achievements,
                      CampaignTrophyRepository trophies, SnapshotBattles battles, CatalogService catalog,
                      AccessService access, AccountService accounts, ExpansionSettings expansions,
                      PrimalProperties properties, Clock clock) {
        this.campaigns = campaigns;
        this.repository = repository;
        this.hunters = hunters;
        this.skills = skills;
        this.resources = resources;
        this.items = items;
        this.quests = quests;
        this.achievements = achievements;
        this.trophies = trophies;
        this.battles = battles;
        this.catalog = catalog;
        this.access = access;
        this.accounts = accounts;
        this.expansions = expansions;
        this.properties = properties;
        this.clock = clock;
    }

    /** Снимок кампании; нет доступа — {@code 404}. */
    @Transactional(readOnly = true)
    public CampaignSnapshot read(PrimalPrincipal principal, long campaignId) {
        CampaignAccess campaignAccess = access.require(principal, campaignId);
        return snapshot(campaigns.campaign(campaignId), campaignAccess);
    }

    /**
     * Запись снимка целиком. Состояние должно быть основано на актуальной версии, иначе
     * {@code 409 VERSION_CONFLICT}: приложение запрашивает снимок заново, повторяет свои действия поверх него и
     * отправляет снова. Состав отряда снимок не меняет — охотники сопоставляются по классу.
     */
    @Transactional
    public CampaignSnapshot replace(PrimalPrincipal principal, long campaignId, int expectedVersion, SnapshotState state) {
        CampaignAccess campaignAccess = access.require(principal, campaignId);
        Campaign campaign = repository.lockById(campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Кампания не найдена."));
        if (campaign.getVersion() != expectedVersion) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "Кампания уже изменена на другом устройстве (версия "
                    + campaign.getVersion() + ", ожидалась " + expectedVersion + ").");
        }
        SnapshotState valid = validate(state);
        Set<UUID> battleIds = battles.merge(principal, campaignId, valid.battles());
        write(campaign, valid, battleIds);
        campaigns.touch(campaign);
        return snapshot(campaign, campaignAccess);
    }

    /**
     * Кампания, созданная в приложении (в том числе без сети), — на сервер: владелец — пользователь запроса.
     * Те же ограничения, что у создания: только с аккаунтом, 2–5 охотников разных классов, не больше 10 своих
     * кампаний.
     */
    @Transactional
    public CampaignSnapshot importCampaign(PrimalPrincipal principal, SnapshotState state) {
        if (!(principal instanceof UserPrincipal user)) {
            throw new ApiException(ErrorCode.ACCOUNT_REQUIRED, "Загружать кампании можно после входа.");
        }
        SnapshotState valid = validate(state);
        PrimalProperties.Hunters limits = properties.campaign().hunters();
        if (valid.hunters().size() < limits.min() || valid.hunters().size() > limits.max()) {
            throw invalid("hunters", "В отряде от " + limits.min() + " до " + limits.max() + " охотников");
        }
        if (repository.countByOwnerId(user.userId()) >= properties.campaign().maxPerUser()) {
            throw new ApiException(ErrorCode.CAMPAIGN_LIMIT_REACHED,
                    "У вас уже " + properties.campaign().maxPerUser() + " кампаний. Удалите ненужную, чтобы загрузить новую.");
        }
        Instant now = clock.instant();
        Campaign campaign = repository.saveAndFlush(new Campaign(user.userId(), valid.name(), now));
        List<SnapshotHunter> squad = valid.hunters().stream()
                .sorted(Comparator.comparingInt(SnapshotHunter::position))
                .toList();
        for (int index = 0; index < squad.size(); index++) {
            SnapshotHunter hunter = squad.get(index);
            hunters.saveAndFlush(new CampaignHunter(campaign.getId(), hunter.hunterClass(), playerName(hunter), index + 1));
        }
        Set<UUID> battleIds = battles.merge(principal, campaign.getId(), valid.battles());
        write(campaign, valid, battleIds);
        campaigns.touch(campaign);
        return snapshot(campaign, CampaignAccess.OWNER);
    }

    // region Запись

    private void write(Campaign campaign, SnapshotState state, Set<UUID> battleIds) {
        Instant now = clock.instant();
        long campaignId = campaign.getId();

        Map<HunterClass, CampaignHunter> squad = hunters.findByCampaignIdOrderByPosition(campaignId).stream()
                .collect(Collectors.toMap(CampaignHunter::getHunterClass, Function.identity()));
        for (SnapshotHunter snapshot : state.hunters()) {
            CampaignHunter hunter = squad.get(snapshot.hunterClass());
            if (hunter != null) {
                writeHunter(hunter, snapshot, now);
            }
        }

        Map<Integer, CampaignQuest> existingQuests = quests.findByKeyCampaignId(campaignId).stream()
                .collect(Collectors.toMap(CampaignQuest::getQuestNumber, Function.identity()));
        Set<Integer> wanted = state.quests().stream().map(SnapshotQuest::number).collect(Collectors.toSet());
        existingQuests.values().stream().filter(quest -> !wanted.contains(quest.getQuestNumber())).forEach(quests::delete);
        quests.flush();
        for (SnapshotQuest snapshot : state.quests()) {
            CampaignQuest quest = existingQuests.get(snapshot.number());
            if (quest == null) {
                quest = new CampaignQuest(campaignId, snapshot.number(), snapshot.openedInChapter(), now);
            }
            quest.restore(snapshot.status(), snapshot.openedInChapter(), snapshot.closedInChapter(), now);
            quests.save(quest);
        }
        quests.flush();

        // Достижения и трофеи заменяются целиком: на них нет ссылок, а уникальность названий проверяет БД
        achievements.deleteAll(achievements.findByCampaignIdOrderById(campaignId));
        achievements.flush();
        for (SnapshotAchievement snapshot : state.achievements()) {
            String normalized = AchievementNames.normalize(snapshot.name());
            achievements.save(new CampaignAchievement(campaignId, snapshot.code(), snapshot.name(), normalized,
                    snapshot.source(), snapshot.grantedInChapter(), now));
        }
        achievements.flush();

        trophies.deleteAll(trophies.findByCampaignIdOrderByChapterAscIdAsc(campaignId));
        trophies.flush();
        Set<UUID> usedBattles = new HashSet<>();
        for (SnapshotTrophy snapshot : state.trophies()) {
            UUID battleId = snapshot.battleId() != null && battleIds.contains(snapshot.battleId())
                    && usedBattles.add(snapshot.battleId()) ? snapshot.battleId() : null;
            trophies.save(new CampaignTrophy(campaignId, snapshot.bossCode(), snapshot.chapter(), battleId, now));
        }
        trophies.flush();
        // Строка кампании — последней: версия растёт один раз, при сохранении в touch
        campaign.restore(state.name(), state.notes(), state.chapter(), state.status(), state.forgeLevel(),
                state.labLevel(), state.finalBossCode(), state.progressSeq());
    }

    private void writeHunter(CampaignHunter hunter, SnapshotHunter snapshot, Instant now) {
        long hunterId = hunter.getId();
        String playerName = playerName(snapshot);
        if (!playerName.equals(hunter.getPlayerName())) {
            hunter.rename(playerName);
            hunters.saveAndFlush(hunter);
        }

        Set<Skill> wantedSkills = snapshot.skills().stream()
                .map(skill -> new Skill(skill.branch(), skill.tier()))
                .collect(Collectors.toSet());
        List<HunterSkill> existingSkills = skills.findByKeyHunterIdIn(List.of(hunterId));
        Set<Skill> present = new HashSet<>();
        for (HunterSkill skill : existingSkills) {
            if (wantedSkills.contains(skill.toSkill())) {
                present.add(skill.toSkill());
            } else {
                skills.delete(skill);
            }
        }
        skills.flush();
        wantedSkills.stream()
                .filter(skill -> !present.contains(skill))
                .forEach(skill -> skills.save(new HunterSkill(hunterId, skill.branch(), skill.tier(), now)));
        skills.flush();

        resources.deleteByHunter(hunterId);
        snapshot.resources().forEach((code, quantity) -> {
            if (quantity > 0) {
                resources.add(hunterId, code, quantity);
            }
        });

        Map<Long, HunterItem> existingItems = items.findByHunterIdInOrderById(List.of(hunterId)).stream()
                .collect(Collectors.toMap(HunterItem::getId, Function.identity()));
        // Карта с тем же id и видом правится на месте (её id видят другие участники), остальные — заново
        Set<Long> kept = snapshot.items().stream()
                .filter(item -> item.id() != null && existingItems.containsKey(item.id())
                        && existingItems.get(item.id()).getKind() == item.kind())
                .map(SnapshotItem::id)
                .collect(Collectors.toSet());
        existingItems.values().stream().filter(item -> !kept.contains(item.getId())).forEach(items::delete);
        // Удаления — до новых карт: зелье каждого вида одно (уникальный индекс), а Hibernate вставляет раньше удаления
        items.flush();
        Set<Long> edited = new HashSet<>();
        for (SnapshotItem item : snapshot.items()) {
            HunterItem existing = item.id() == null || !kept.contains(item.id()) ? null : existingItems.get(item.id());
            if (existing != null && edited.add(existing.getId())) {
                existing.edit(item.name(), item.level());
            } else {
                items.save(new HunterItem(hunterId, item.kind(), item.name(), item.level(), item.element(), item.source(), now));
            }
        }
        items.flush();
    }

    private static String playerName(SnapshotHunter hunter) {
        return hunter.playerName() == null || hunter.playerName().isBlank()
                ? hunter.hunterClass().displayName()
                : hunter.playerName().strip();
    }

    // endregion

    // region Чтение

    CampaignSnapshot snapshot(Campaign campaign, CampaignAccess campaignAccess) {
        long campaignId = campaign.getId();
        List<CampaignHunter> squad = hunters.findByCampaignIdOrderByPosition(campaignId);
        List<Long> hunterIds = squad.stream().map(CampaignHunter::getId).toList();
        Map<Long, List<SnapshotSkill>> skillsByHunter = new HashMap<>();
        skills.findByKeyHunterIdIn(hunterIds).stream()
                .sorted(Comparator.comparing((HunterSkill skill) -> skill.toSkill().branch()).thenComparingInt(skill -> skill.toSkill().tier()))
                .forEach(skill -> skillsByHunter.computeIfAbsent(skill.getHunterId(), key -> new ArrayList<>())
                        .add(new SnapshotSkill(skill.toSkill().branch(), skill.toSkill().tier())));
        Map<Long, Map<ResourceCode, Integer>> resourcesByHunter = new HashMap<>();
        resources.findByKeyHunterIdIn(hunterIds).forEach(resource -> {
            if (resource.getQuantity() > 0) {
                resourcesByHunter.computeIfAbsent(resource.getHunterId(), key -> new EnumMap<>(ResourceCode.class))
                        .put(resource.getResource(), resource.getQuantity());
            }
        });
        Map<Long, List<SnapshotItem>> itemsByHunter = new HashMap<>();
        items.findByHunterIdInOrderById(hunterIds).forEach(item -> itemsByHunter
                .computeIfAbsent(item.getHunterId(), key -> new ArrayList<>())
                .add(new SnapshotItem(item.getId(), item.getKind(), item.getName(), item.getLevel(), item.getElement(),
                        item.getSource())));

        List<SnapshotHunter> squadView = squad.stream()
                .map(hunter -> {
                    Map<String, Integer> stock = new LinkedHashMap<>();
                    resourcesByHunter.getOrDefault(hunter.getId(), Map.of())
                            .forEach((code, quantity) -> stock.put(code.name(), quantity));
                    return new SnapshotHunter(hunter.getId(), hunter.getHunterClass(), hunter.getPlayerName(),
                            hunter.getPosition(), skillsByHunter.getOrDefault(hunter.getId(), List.of()), stock,
                            itemsByHunter.getOrDefault(hunter.getId(), List.of()));
                })
                .toList();
        List<SnapshotQuest> questViews = quests.findByKeyCampaignId(campaignId).stream()
                .sorted(Comparator.comparingInt(CampaignQuest::getQuestNumber))
                .map(quest -> new SnapshotQuest(quest.getQuestNumber(), quest.getStatus(), quest.getOpenedInChapter(),
                        quest.getClosedInChapter()))
                .toList();
        List<SnapshotAchievement> achievementViews = achievements.findByCampaignIdOrderById(campaignId).stream()
                .map(achievement -> new SnapshotAchievement(achievement.getId(), achievement.getAchievementCode(),
                        achievement.getName(), achievement.getSource(), achievement.getGrantedInChapter()))
                .toList();
        List<SnapshotTrophy> trophyViews = trophies.findByCampaignIdOrderByChapterAscIdAsc(campaignId).stream()
                .map(trophy -> new SnapshotTrophy(trophy.getBossCode(), trophy.getChapter(), trophy.getCampaignBattleId()))
                .toList();
        List<SnapshotBattle> battleViews = battles.list(campaignId);

        SnapshotState state = new SnapshotState(campaign.getName(), campaign.getChapter(), campaign.getStatus(),
                campaign.getForgeLevel(), campaign.getLabLevel(), campaign.getFinalBossCode(), campaign.getProgressSeq(),
                campaign.getNotes(), squadView, questViews, achievementViews, trophyViews, battleViews);
        return new CampaignSnapshot(campaignId, campaign.getVersion(), campaignAccess,
                accounts.userName(campaign.getOwnerId()),
                expansions.enabled(campaign.getOwnerId()).stream().sorted().toList(),
                campaign.getCreatedAt(), campaign.getUpdatedAt(), state);
    }

    // endregion

    // region Проверка

    /**
     * Формат и ссылки на каталог: глава, уровни, задания, достижения, боссы, ресурсы, навыки. Пустые списки можно
     * не передавать. Повтор достижения в другом написании отбрасывается (D-9).
     */
    private SnapshotState validate(SnapshotState state) {
        if (state == null) {
            throw invalid("state", "Нет состояния кампании.");
        }
        String name = state.name() == null ? "" : state.name().strip();
        if (name.isEmpty() || name.length() > 100) {
            throw invalid("name", "Название кампании — от 1 до 100 символов.");
        }
        String notes = state.notes() == null ? "" : state.notes();
        if (notes.length() > MAX_NOTES) {
            throw invalid("notes", "Заметки — не длиннее 20 000 символов.");
        }
        require(state.chapter() >= 0 && state.chapter() <= 11, "chapter", "Глава — от 0 до 11.");
        require(state.status() != null, "status", "Нет статуса кампании.");
        require(state.forgeLevel() >= 1 && state.forgeLevel() <= 3, "forgeLevel", "Уровень кузни — от 1 до 3.");
        require(state.labLevel() >= 1 && state.labLevel() <= 3, "labLevel", "Уровень лаборатории — от 1 до 3.");
        require(state.finalBossCode() == null || catalog.boss(state.finalBossCode()).isPresent(), "finalBossCode",
                "Неизвестный босс: " + state.finalBossCode() + ".");
        require(state.progressSeq() >= 0, "progressSeq", "progressSeq не может быть отрицательным.");

        List<SnapshotHunter> squad = list(state.hunters());
        Set<HunterClass> classes = new HashSet<>();
        List<SnapshotHunter> hunterViews = new ArrayList<>();
        for (SnapshotHunter hunter : squad) {
            require(hunter.hunterClass() != null && classes.add(hunter.hunterClass()), "hunters",
                    "Классы охотников не должны повторяться.");
            require(hunter.playerName() == null || hunter.playerName().strip().length() <= 60, "hunters",
                    "Имя игрока — не длиннее 60 символов.");
            Set<Skill> unlocked = new HashSet<>();
            for (SnapshotSkill skill : list(hunter.skills())) {
                require(skill.branch() != null && (skill.tier() == 1 || skill.tier() == 2), "skills", "Неверная ступень навыка.");
                unlocked.add(new Skill(skill.branch(), skill.tier()));
            }
            for (Skill skill : unlocked) {
                require(skill.tier() == 1 || unlocked.contains(new Skill(skill.branch(), 1)), "skills",
                        "Ступень 2 открывается только после ступени 1 той же ветви.");
            }
            Map<String, Integer> stock = new LinkedHashMap<>();
            (hunter.resources() == null ? Map.<String, Integer>of() : hunter.resources()).forEach((code, quantity) -> {
                require(resourceCode(code).isPresent(), "resources", "Неизвестный ресурс: " + code + ".");
                require(quantity != null && quantity >= 0, "resources", "Количество ресурса не может быть отрицательным.");
                if (quantity > 0) {
                    stock.put(code, quantity);
                }
            });
            List<SnapshotItem> inventory = new ArrayList<>();
            Map<String, Integer> potionIndex = new HashMap<>();
            for (SnapshotItem item : list(hunter.items())) {
                require(item.kind() != null, "items", "Нет вида предмета.");
                String itemName = item.name() == null ? "" : item.name().strip();
                require(!itemName.isEmpty() && itemName.length() <= 100, "items", "Название предмета — от 1 до 100 символов.");
                Integer level = item.kind() == HunterItem.Kind.REWARD ? null : (item.level() == null ? 1 : item.level());
                require(level == null || (level >= 1 && level <= 3), "items", "Уровень предмета — от 1 до 3.");
                require(item.source() == null || item.source().length() <= 16, "items", "Слишком длинный код предмета.");
                if (item.kind() == HunterItem.Kind.POTION) {
                    // Зелье каждого вида одно: повтор сводится к первому с наибольшим уровнем
                    String key = itemName.toLowerCase(java.util.Locale.ROOT);
                    Integer index = potionIndex.get(key);
                    if (index != null) {
                        SnapshotItem first = inventory.get(index);
                        int merged = Math.max(first.level() == null ? 1 : first.level(), level == null ? 1 : level);
                        inventory.set(index, new SnapshotItem(first.id(), first.kind(), first.name(), merged, first.element(),
                                first.source()));
                        continue;
                    }
                    potionIndex.put(key, inventory.size());
                }
                inventory.add(new SnapshotItem(item.id(), item.kind(), itemName, level, item.element(), item.source()));
            }
            hunterViews.add(new SnapshotHunter(hunter.id(), hunter.hunterClass(), hunter.playerName(), hunter.position(),
                    List.copyOf(unlocked.stream().map(skill -> new SnapshotSkill(skill.branch(), skill.tier())).toList()),
                    stock, inventory));
        }

        Set<Integer> questNumbers = new HashSet<>();
        for (SnapshotQuest quest : list(state.quests())) {
            require(catalog.quest(quest.number()).isPresent(), "quests", "Нет задания " + quest.number() + ".");
            require(questNumbers.add(quest.number()), "quests", "Задание " + quest.number() + " указано дважды.");
            require(quest.status() != null, "quests", "Нет статуса задания " + quest.number() + ".");
            require(quest.openedInChapter() >= 0 && quest.openedInChapter() <= 11, "quests", "Глава — от 0 до 11.");
            require(quest.closedInChapter() == null || (quest.closedInChapter() >= 0 && quest.closedInChapter() <= 11),
                    "quests", "Глава — от 0 до 11.");
        }

        List<SnapshotAchievement> achievementViews = new ArrayList<>();
        Set<String> normalizedNames = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (SnapshotAchievement achievement : list(state.achievements())) {
            Optional<AchievementDef> known = achievement.code() == null
                    ? catalog.achievementByName(achievement.name() == null ? "" : achievement.name())
                    : catalog.achievements().stream().filter(def -> def.code().equals(achievement.code())).findFirst();
            require(achievement.code() == null || known.isPresent(), "achievements",
                    "Нет достижения " + achievement.code() + ".");
            String typed = achievement.name() == null ? "" : achievement.name().strip().replaceAll("\\s+", " ");
            String achievementName = known.map(AchievementDef::name).orElse(typed);
            require(!achievementName.isEmpty() && achievementName.length() <= 100, "achievements",
                    "Название достижения — от 1 до 100 символов.");
            String code = known.map(AchievementDef::code).orElse(null);
            String normalized = AchievementNames.normalize(achievementName);
            if (!normalizedNames.add(normalized) || (code != null && !codes.add(code))) {
                continue;
            }
            achievementViews.add(new SnapshotAchievement(achievement.id(), code, achievementName,
                    achievement.source() == null ? CampaignAchievement.Source.MANUAL : achievement.source(),
                    Math.max(0, Math.min(11, achievement.grantedInChapter()))));
        }

        for (SnapshotTrophy trophy : list(state.trophies())) {
            require(trophy.bossCode() != null && catalog.boss(trophy.bossCode()).isPresent(), "trophies",
                    "Неизвестный босс: " + trophy.bossCode() + ".");
            require(trophy.chapter() >= 0 && trophy.chapter() <= 11, "trophies", "Глава — от 0 до 11.");
        }

        return new SnapshotState(name, state.chapter(), state.status(), state.forgeLevel(), state.labLevel(),
                state.finalBossCode(), state.progressSeq(), notes, hunterViews, list(state.quests()), achievementViews,
                list(state.trophies()), list(state.battles()));
    }

    private static Optional<ResourceCode> resourceCode(String code) {
        try {
            return Optional.of(ResourceCode.valueOf(code));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    private static <T> List<T> list(List<T> items) {
        return items == null ? List.of() : items;
    }

    private static void require(boolean condition, String field, String message) {
        if (!condition) {
            throw invalid(field, message);
        }
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message)
                .with("errors", List.of(Map.of("field", field, "message", message)));
    }

    // endregion
}
