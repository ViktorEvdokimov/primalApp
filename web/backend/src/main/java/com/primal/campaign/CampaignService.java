package com.primal.campaign;

import com.primal.access.AccessService;
import com.primal.access.AccessService.CampaignAccess;
import com.primal.campaign.CampaignSheetDto.CampaignSheet;
import com.primal.campaign.CampaignSheetDto.CampaignSummary;
import com.primal.campaign.CampaignSheetDto.HunterSummary;
import com.primal.campaign.CampaignSheetDto.QuestItem;
import com.primal.common.config.PrimalProperties;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.AccountService;
import com.primal.identity.PrimalPrincipal;
import com.primal.identity.PrimalPrincipal.UserPrincipal;
import com.primal.realtime.ChangeEvents;
import com.primal.rules.model.HunterClass;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Кампании: список, создание, правка, удаление ({@code doc/api.md} §5.1–5.3). */
@Service
public class CampaignService {

    /** Новый охотник отряда: пустое имя игрока заменяется названием класса. */
    public record NewHunter(HunterClass hunterClass, String playerName) {
    }

    /** Правка кампании: {@code null} — поле не меняется. */
    public record CampaignChanges(int expectedVersion, String name, String notes, Integer chapter) {
    }

    /** Состояние кампании, от которого зависят подготовка и старт боя ({@code api.md} §6). */
    public record BattleContext(long campaignId, int chapter, int progressSeq, Campaign.Status status,
                                String finalBossCode, int hunterCount, List<QuestItem> openQuests) {
    }

    private final CampaignRepository campaigns;
    private final CampaignHunterRepository hunters;
    private final CampaignSheetService sheets;
    private final AccessService access;
    private final AccountService accounts;
    private final PrimalProperties properties;
    private final ChangeEvents events;
    private final Clock clock;

    CampaignService(CampaignRepository campaigns, CampaignHunterRepository hunters, CampaignSheetService sheets,
                    AccessService access, AccountService accounts, PrimalProperties properties, ChangeEvents events,
                    Clock clock) {
        this.campaigns = campaigns;
        this.hunters = hunters;
        this.sheets = sheets;
        this.access = access;
        this.accounts = accounts;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    /** Свои кампании и открытые по ссылкам, недавно изменённые сверху. */
    @Transactional(readOnly = true)
    public List<CampaignSummary> list(PrimalPrincipal principal) {
        Map<Long, CampaignAccess> accessById = new LinkedHashMap<>();
        List<Campaign> found = new ArrayList<>();
        if (principal instanceof UserPrincipal user) {
            campaigns.findByOwnerIdOrderByUpdatedAtDesc(user.userId()).forEach(campaign -> {
                found.add(campaign);
                accessById.put(campaign.getId(), CampaignAccess.OWNER);
            });
        }
        campaigns.findAllById(access.linkedCampaignIds(principal)).forEach(campaign -> {
            if (accessById.putIfAbsent(campaign.getId(), CampaignAccess.LINK) == null) {
                found.add(campaign);
            }
        });
        found.sort(Comparator.comparing(Campaign::getUpdatedAt).reversed());

        Map<Long, List<HunterSummary>> squads = new LinkedHashMap<>();
        hunters.findByCampaignIdInOrderByPosition(accessById.keySet()).forEach(hunter ->
                squads.computeIfAbsent(hunter.getCampaignId(), key -> new ArrayList<>())
                        .add(new HunterSummary(hunter.getHunterClass(), hunter.getPlayerName())));
        return found.stream()
                .map(campaign -> new CampaignSummary(campaign.getId(), campaign.getName(), campaign.getChapter(),
                        campaign.getStatus(), accessById.get(campaign.getId()), accounts.userName(campaign.getOwnerId()),
                        squads.getOrDefault(campaign.getId(), List.of()),
                        campaign.getStatus() == Campaign.Status.CHAPTER_TRANSITION, campaign.getUpdatedAt()))
                .toList();
    }

    /** Новая кампания начинается с главы 0 «Пролог». Создают кампании только пользователи с аккаунтом. */
    @Transactional
    public CampaignSheet create(PrimalPrincipal principal, String name, List<NewHunter> squad) {
        if (!(principal instanceof UserPrincipal user)) {
            throw new ApiException(ErrorCode.ACCOUNT_REQUIRED, "Создавать кампании можно после входа по почте.");
        }
        validateSquad(squad);
        if (campaigns.countByOwnerId(user.userId()) >= properties.campaign().maxPerUser()) {
            throw new ApiException(ErrorCode.CAMPAIGN_LIMIT_REACHED,
                    "У вас уже " + properties.campaign().maxPerUser() + " кампаний. Удалите ненужную, чтобы создать новую.");
        }
        Instant now = clock.instant();
        Campaign campaign = campaigns.saveAndFlush(new Campaign(user.userId(), name.strip(), now));
        for (int i = 0; i < squad.size(); i++) {
            NewHunter hunter = squad.get(i);
            String playerName = hunter.playerName() == null || hunter.playerName().isBlank()
                    ? hunter.hunterClass().displayName()
                    : hunter.playerName().strip();
            hunters.save(new CampaignHunter(campaign.getId(), hunter.hunterClass(), playerName, i + 1));
        }
        hunters.flush();
        return sheets.sheet(campaign, CampaignAccess.OWNER);
    }

    @Transactional(readOnly = true)
    public CampaignSheet sheet(PrimalPrincipal principal, long campaignId) {
        CampaignAccess campaignAccess = access.require(principal, campaignId);
        return sheets.sheet(campaign(campaignId), campaignAccess);
    }

    /**
     * Название, заметки и ручная правка главы. Устаревшая версия — {@code 409 VERSION_CONFLICT} с актуальным
     * листом; правка главы при ожидающем переходе — {@code 409 CHAPTER_TRANSITION_PENDING}.
     */
    @Transactional
    public CampaignSheet update(PrimalPrincipal principal, long campaignId, CampaignChanges changes) {
        CampaignAccess campaignAccess = access.require(principal, campaignId);
        Campaign campaign = campaign(campaignId);
        requireVersion(campaign, changes.expectedVersion(), campaignAccess);
        if (changes.chapter() != null && changes.chapter() != campaign.getChapter()) {
            if (campaign.getStatus() == Campaign.Status.CHAPTER_TRANSITION) {
                throw new ApiException(ErrorCode.CHAPTER_TRANSITION_PENDING, "Сначала завершите переход главы.");
            }
            campaign.changeChapter(changes.chapter());
        }
        if (changes.name() != null) {
            campaign.rename(changes.name().strip());
        }
        if (changes.notes() != null) {
            campaign.setNotes(changes.notes());
        }
        campaign.touch(clock.instant());
        events.campaignChanged(campaignId);
        return sheets.sheet(campaigns.saveAndFlush(campaign), campaignAccess);
    }

    /** Состояние для боя; доступ проверяет вызывающий. Нет кампании — {@code 404}. */
    @Transactional(readOnly = true)
    public BattleContext battleContext(long campaignId) {
        return battleContext(campaign(campaignId));
    }

    /**
     * Состояние для итога боя с блокировкой кампании до конца транзакции: два итога одной главы не проверяются
     * одновременно, поэтому главу закрывает ровно одна победа.
     */
    @Transactional
    public BattleContext lockForBattle(long campaignId) {
        return battleContext(campaigns.lockById(campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Кампания не найдена.")));
    }

    private BattleContext battleContext(Campaign campaign) {
        long campaignId = campaign.getId();
        return new BattleContext(campaignId, campaign.getChapter(), campaign.getProgressSeq(), campaign.getStatus(),
                campaign.getFinalBossCode(), (int) hunters.countByCampaignId(campaignId),
                sheets.questLists(campaignId).open());
    }

    /** Удаление — только владельцу; всё, что относится к кампании, удаляется каскадом в БД. */
    @Transactional
    public void delete(PrimalPrincipal principal, long campaignId) {
        access.requireOwner(principal, campaignId);
        campaigns.deleteById(campaignId);
        events.accessChanged(campaignId);
    }

    /** Кампания изменилась: растёт версия (охотники, навыки, ресурсы, задания, достижения меняют её тоже). */
    void touch(Campaign campaign) {
        campaign.touch(clock.instant());
        campaigns.saveAndFlush(campaign);
        events.campaignChanged(campaign.getId());
    }

    Campaign campaign(long campaignId) {
        return campaigns.findById(campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Кампания не найдена."));
    }

    void requireVersion(Campaign campaign, int expectedVersion, CampaignAccess campaignAccess) {
        if (campaign.getVersion() != expectedVersion) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "Кампания уже изменена на другом устройстве (версия "
                    + campaign.getVersion() + ", ожидалась " + expectedVersion + ").")
                    .with("current", sheets.sheet(campaign, campaignAccess));
        }
    }

    private void validateSquad(List<NewHunter> squad) {
        PrimalProperties.Hunters limits = properties.campaign().hunters();
        if (squad.size() < limits.min() || squad.size() > limits.max()) {
            throw invalidSquad("В отряде от " + limits.min() + " до " + limits.max() + " охотников");
        }
        Set<HunterClass> classes = new HashSet<>();
        for (NewHunter hunter : squad) {
            if (!classes.add(hunter.hunterClass())) {
                throw invalidSquad("Классы охотников не должны повторяться");
            }
        }
    }

    private static ApiException invalidSquad(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message)
                .with("errors", List.of(Map.of("field", "hunters", "message", message)));
    }
}
