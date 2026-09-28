package com.primal.campaign;

import com.primal.access.AccessService.CampaignAccess;
import com.primal.campaign.CampaignSheetDto.AchievementItem;
import com.primal.campaign.CampaignSheetDto.CampaignBoss;
import com.primal.campaign.CampaignSheetDto.CampaignSheet;
import com.primal.campaign.CampaignSheetDto.HunterSheet;
import com.primal.campaign.CampaignSheetDto.QuestItem;
import com.primal.campaign.CampaignSheetDto.QuestLists;
import com.primal.campaign.CampaignSheetDto.SkillStep;
import com.primal.campaign.CampaignSheetDto.TrophyItem;
import com.primal.identity.AccountService;
import com.primal.rules.model.Difficulty;
import com.primal.rules.model.ResourceCode;
import com.primal.rules.model.SkillTree;
import com.primal.rules.model.SkillTree.Skill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Лист кампании одним ответом: клиенту не нужны отдельные запросы к каталогу ({@code doc/api.md} §5.2). */
@Service
public class CampaignSheetService {

    private final CampaignHunterRepository hunters;
    private final HunterSkillRepository skills;
    private final HunterResourceRepository resources;
    private final CampaignQuestRepository quests;
    private final CampaignAchievementRepository achievements;
    private final CampaignTrophyRepository trophies;
    private final CampaignViews views;
    private final CampaignBattles battles;
    private final AccountService accounts;

    CampaignSheetService(CampaignHunterRepository hunters, HunterSkillRepository skills,
                         HunterResourceRepository resources, CampaignQuestRepository quests,
                         CampaignAchievementRepository achievements, CampaignTrophyRepository trophies,
                         CampaignViews views, CampaignBattles battles, AccountService accounts) {
        this.hunters = hunters;
        this.skills = skills;
        this.resources = resources;
        this.quests = quests;
        this.achievements = achievements;
        this.trophies = trophies;
        this.views = views;
        this.battles = battles;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public CampaignSheet sheet(Campaign campaign, CampaignAccess access) {
        long id = campaign.getId();
        return new CampaignSheet(
                id,
                campaign.getName(),
                campaign.getVersion(),
                campaign.getChapter(),
                campaign.getStatus(),
                access,
                accounts.userName(campaign.getOwnerId()),
                Difficulty.forChapter(campaign.getChapter()),
                campaign.getForgeLevel(),
                campaign.getLabLevel(),
                campaign.getFinalBossCode() == null ? null : boss(campaign.getFinalBossCode()),
                campaign.getNotes(),
                hunterSheets(id),
                questLists(id),
                achievements.findByCampaignIdOrderById(id).stream()
                        .map(a -> new AchievementItem(a.getId(), a.getAchievementCode(), a.getName(), a.getSource(),
                                a.getGrantedInChapter()))
                        .toList(),
                trophyItems(id),
                battles.active(id, campaign.getProgressSeq()),
                battles.recent(id),
                campaign.getStatus() == Campaign.Status.CHAPTER_TRANSITION);
    }

    /** Охотник листа: навыки, доступные ступени, ненулевые ресурсы в порядке справочника. */
    public HunterSheet hunterSheet(CampaignHunter hunter) {
        return hunterSheets(List.of(hunter)).getFirst();
    }

    private List<HunterSheet> hunterSheets(long campaignId) {
        return hunterSheets(hunters.findByCampaignIdOrderByPosition(campaignId));
    }

    private List<HunterSheet> hunterSheets(List<CampaignHunter> squad) {
        List<Long> ids = squad.stream().map(CampaignHunter::getId).toList();
        Map<Long, Set<Skill>> skillsByHunter = skills.findByKeyHunterIdIn(ids).stream()
                .collect(Collectors.groupingBy(HunterSkill::getHunterId, Collectors.mapping(HunterSkill::toSkill, Collectors.toSet())));
        Map<Long, Map<ResourceCode, Integer>> resourcesByHunter = new LinkedHashMap<>();
        for (HunterResource resource : resources.findByKeyHunterIdIn(ids)) {
            if (resource.getQuantity() != 0) {
                resourcesByHunter.computeIfAbsent(resource.getHunterId(), key -> new EnumMap<>(ResourceCode.class))
                        .put(resource.getResource(), resource.getQuantity());
            }
        }
        return squad.stream().map(hunter -> {
            Set<Skill> unlocked = skillsByHunter.getOrDefault(hunter.getId(), Set.of());
            Map<String, Integer> owned = new LinkedHashMap<>();
            resourcesByHunter.getOrDefault(hunter.getId(), Map.of()).forEach((code, quantity) -> owned.put(code.name(), quantity));
            return new HunterSheet(
                    hunter.getId(),
                    hunter.getHunterClass(),
                    hunter.getPlayerName(),
                    hunter.getPosition(),
                    unlocked.stream()
                            .sorted(Comparator.comparing(Skill::branch).thenComparing(Skill::tier))
                            .map(skill -> new SkillStep(skill.branch(), skill.tier()))
                            .toList(),
                    SkillTree.unlockable(unlocked).stream().map(skill -> new SkillStep(skill.branch(), skill.tier())).toList(),
                    owned);
        }).toList();
    }

    QuestLists questLists(long campaignId) {
        List<QuestItem> open = new ArrayList<>();
        List<QuestItem> completed = new ArrayList<>();
        List<QuestItem> expired = new ArrayList<>();
        quests.findByKeyCampaignId(campaignId).stream()
                .sorted(Comparator.comparingInt(CampaignQuest::getQuestNumber))
                .forEach(quest -> {
                    QuestItem item = questItem(quest);
                    switch (quest.getStatus()) {
                        case OPEN -> open.add(item);
                        case COMPLETED -> completed.add(item);
                        case EXPIRED -> expired.add(item);
                    }
                });
        return new QuestLists(open, completed, expired);
    }

    private QuestItem questItem(CampaignQuest quest) {
        return views.quest(quest.getQuestNumber(), quest.getClosedInChapter());
    }

    private List<TrophyItem> trophyItems(long campaignId) {
        Map<String, List<Integer>> chaptersByBoss = new LinkedHashMap<>();
        trophies.findByCampaignIdOrderByChapterAscIdAsc(campaignId).forEach(trophy ->
                chaptersByBoss.computeIfAbsent(trophy.getBossCode(), key -> new ArrayList<>()).add(trophy.getChapter()));
        return chaptersByBoss.entrySet().stream()
                .map(entry -> new TrophyItem(boss(entry.getKey()), entry.getValue()))
                .toList();
    }

    CampaignBoss boss(String code) {
        return views.boss(code);
    }
}
