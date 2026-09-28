package com.primal.campaign;

import com.primal.access.AccessService;
import com.primal.campaign.CampaignSheetDto.HunterSheet;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal;
import com.primal.rules.model.ResourceCode;
import com.primal.rules.model.SkillBranch;
import com.primal.rules.model.SkillTree;
import com.primal.rules.model.SkillTree.Skill;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Охотники: имя игрока, древо навыков (перенос {@code SkillValidatorImpl}) и ресурсы ({@code doc/api.md} §5.4).
 * Любое изменение увеличивает версию кампании.
 */
@Service
public class HunterService {

    private final CampaignService campaigns;
    private final CampaignHunterRepository hunters;
    private final HunterSkillRepository skills;
    private final HunterResourceRepository resources;
    private final CampaignSheetService sheets;
    private final AccessService access;
    private final Clock clock;

    HunterService(CampaignService campaigns, CampaignHunterRepository hunters, HunterSkillRepository skills,
                  HunterResourceRepository resources, CampaignSheetService sheets, AccessService access, Clock clock) {
        this.campaigns = campaigns;
        this.hunters = hunters;
        this.skills = skills;
        this.resources = resources;
        this.sheets = sheets;
        this.access = access;
        this.clock = clock;
    }

    /** Пустое имя игрока заменяется названием класса, как при создании кампании. */
    @Transactional
    public HunterSheet rename(PrimalPrincipal principal, long campaignId, long hunterId, String playerName) {
        CampaignHunter hunter = hunter(principal, campaignId, hunterId);
        hunter.rename(playerName == null || playerName.isBlank() ? hunter.getHunterClass().displayName() : playerName.strip());
        hunters.saveAndFlush(hunter);
        touch(campaignId);
        return sheets.hunterSheet(hunter);
    }

    /** Открыть ступень: ступень 2 — только после ступени 1; открытую повторно не открыть. */
    @Transactional
    public HunterSheet unlockSkill(PrimalPrincipal principal, long campaignId, long hunterId, SkillBranch branch, int tier) {
        CampaignHunter hunter = hunter(principal, campaignId, hunterId);
        Set<Skill> unlocked = unlocked(hunterId);
        if (!SkillTree.canUnlock(unlocked, branch, tier)) {
            throw skillLocked(unlocked.contains(new Skill(branch, tier))
                    ? "Эта ступень уже открыта."
                    : "Ступень 2 открывается только после ступени 1 той же ветви.");
        }
        try {
            skills.saveAndFlush(new HunterSkill(hunterId, branch, tier, clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            throw skillLocked("Эта ступень уже открыта.");
        }
        touch(campaignId);
        return sheets.hunterSheet(hunter);
    }

    /** Снять ступень (исправление ошибки): ступень 1 не снимается, пока открыта ступень 2. */
    @Transactional
    public HunterSheet lockSkill(PrimalPrincipal principal, long campaignId, long hunterId, SkillBranch branch, int tier) {
        CampaignHunter hunter = hunter(principal, campaignId, hunterId);
        Set<Skill> unlocked = unlocked(hunterId);
        if (!SkillTree.canLock(unlocked, branch, tier)) {
            throw skillLocked(unlocked.contains(new Skill(branch, tier))
                    ? "Сначала снимите ступень 2 этой ветви."
                    : "Эта ступень не открыта.");
        }
        skills.deleteById(new HunterSkill.Key(hunterId, branch.name(), (short) tier));
        skills.flush();
        touch(campaignId);
        return sheets.hunterSheet(hunter);
    }

    /**
     * Изменение ресурсов «всё или ничего»: если хоть один ресурс ушёл бы ниже нуля, не меняется ничего
     * ({@code 422 NOT_ENOUGH_RESOURCES}). Возвращает ненулевые количества после изменения.
     */
    @Transactional
    public Map<String, Integer> adjustResources(PrimalPrincipal principal, long campaignId, long hunterId,
                                                Map<ResourceCode, Integer> changes) {
        CampaignHunter hunter = hunter(principal, campaignId, hunterId);
        Map<ResourceCode, Integer> current = quantities(hunterId);
        List<String> missing = changes.entrySet().stream()
                .filter(change -> current.getOrDefault(change.getKey(), 0) + change.getValue() < 0)
                .map(change -> change.getKey().displayName())
                .toList();
        if (!missing.isEmpty()) {
            throw notEnough(missing);
        }
        try {
            changes.forEach((code, delta) -> {
                if (delta > 0) {
                    resources.add(hunterId, code.name(), delta);
                } else if (delta < 0 && resources.subtract(hunterId, code.name(), -delta) == 0) {
                    throw notEnough(List.of(code.displayName()));
                }
            });
            resources.flush();
        } catch (DataIntegrityViolationException exception) {
            // Параллельное изменение успело списать ресурс: CHECK в БД не даёт уйти в минус
            throw notEnough(List.of());
        }
        touch(campaignId);
        return sheets.hunterSheet(hunter).resources();
    }

    private CampaignHunter hunter(PrimalPrincipal principal, long campaignId, long hunterId) {
        access.require(principal, campaignId);
        return hunters.findById(hunterId)
                .filter(hunter -> hunter.getCampaignId() == campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Охотник не найден."));
    }

    private Set<Skill> unlocked(long hunterId) {
        return skills.findByKeyHunterIdIn(List.of(hunterId)).stream().map(HunterSkill::toSkill).collect(Collectors.toSet());
    }

    private Map<ResourceCode, Integer> quantities(long hunterId) {
        Map<ResourceCode, Integer> result = new EnumMap<>(ResourceCode.class);
        resources.quantities(hunterId).forEach(r -> result.put(ResourceCode.valueOf(r.getResource()), r.getQuantity()));
        return result;
    }

    private void touch(long campaignId) {
        campaigns.touch(campaigns.campaign(campaignId));
    }

    private static ApiException skillLocked(String detail) {
        return new ApiException(ErrorCode.SKILL_LOCKED, detail);
    }

    private static ApiException notEnough(List<String> missing) {
        String detail = missing.isEmpty()
                ? "Ресурсов не хватает: их только что изменили на другом устройстве."
                : "Не хватает ресурсов: " + String.join(", ", missing) + ".";
        return new ApiException(ErrorCode.NOT_ENOUGH_RESOURCES, detail);
    }
}
