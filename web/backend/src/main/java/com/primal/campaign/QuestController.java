package com.primal.campaign;

import com.primal.campaign.CampaignSheetDto.QuestLists;
import com.primal.campaign.QuestService.Completed;
import com.primal.common.api.ApiNullable;
import com.primal.identity.PrimalPrincipal;
import com.primal.rules.effects.RuleExplanation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Задания и достижения кампании вручную ({@code doc/api.md} §5.5–5.6). */
@Tag(name = "campaigns", description = "Кампании")
@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}")
class QuestController {

    record QuestState(int number, CampaignQuest.Status status) {
    }

    record RuleView(String description, String result) {
    }

    /** Итог «Выполнено»: задание, открытые им задания и пояснения условий. */
    record CompleteQuestResponse(QuestState quest, List<Integer> opened, List<RuleView> rules) {
    }

    record OpenQuestsRequest(@NotNull(message = "Нет списка заданий") Set<@NotNull Integer> numbers) {
    }

    record AddAchievementRequest(
            @NotBlank(message = "Введите название достижения")
            @Size(max = 100, message = "Название — не длиннее 100 символов") String name) {
    }

    record AchievementView(long id, @ApiNullable String code, String name, CampaignAchievement.Source source,
                           int grantedInChapter, boolean matchedCatalog) {
    }

    private final QuestService quests;
    private final AchievementService achievements;

    QuestController(QuestService quests, AchievementService achievements) {
        this.quests = quests;
        this.achievements = achievements;
    }

    @Operation(operationId = "completeQuest")
    @PostMapping("/quests/{number}/complete")
    CompleteQuestResponse complete(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                                   @PathVariable int number) {
        Completed completed = quests.complete(principal, campaignId, number);
        return new CompleteQuestResponse(new QuestState(completed.number(), CampaignQuest.Status.COMPLETED),
                completed.opened(), completed.rules().stream().map(QuestController::rule).toList());
    }

    @Operation(operationId = "reopenQuest")
    @PostMapping("/quests/{number}/reopen")
    QuestLists reopen(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                      @PathVariable int number) {
        return quests.reopen(principal, campaignId, number);
    }

    @Operation(operationId = "setOpenQuests")
    @PutMapping("/quests/open")
    QuestLists setOpen(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                       @Valid @RequestBody OpenQuestsRequest body) {
        return quests.setOpen(principal, campaignId, body.numbers());
    }

    @Operation(operationId = "addAchievement")
    @ApiResponse(responseCode = "201", description = "Достижение добавлено")
    @PostMapping("/achievements")
    ResponseEntity<AchievementView> addAchievement(@AuthenticationPrincipal PrimalPrincipal principal,
                                                   @PathVariable long campaignId, @Valid @RequestBody AddAchievementRequest body) {
        AchievementService.Added added = achievements.add(principal, campaignId, body.name());
        CampaignAchievement a = added.achievement();
        return ResponseEntity.created(URI.create("/api/v1/campaigns/" + campaignId + "/achievements/" + a.getId()))
                .body(new AchievementView(a.getId(), a.getAchievementCode(), a.getName(), a.getSource(),
                        a.getGrantedInChapter(), added.matchedCatalog()));
    }

    @Operation(operationId = "deleteAchievement")
    @ApiResponse(responseCode = "204", description = "Достижение удалено")
    @DeleteMapping("/achievements/{achievementId}")
    ResponseEntity<Void> deleteAchievement(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                                           @PathVariable long achievementId) {
        achievements.delete(principal, campaignId, achievementId);
        return ResponseEntity.noContent().build();
    }

    private static RuleView rule(RuleExplanation explanation) {
        return new RuleView(explanation.description(), explanation.result());
    }
}
