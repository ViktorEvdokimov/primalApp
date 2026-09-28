package com.primal.progression;

import com.primal.campaign.CampaignSheetDto.CampaignSheet;
import com.primal.common.api.ApiNullable;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal;
import com.primal.progression.ChapterTransitionDto.TransitionPreview;
import com.primal.progression.ChapterTransitionService.Action;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Переход главы: превью, «Принять», «Отклонить» ({@code doc/api.md} §8). */
@Tag(name = "chapters", description = "Переход главы")
@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/chapter-transition")
class ChapterTransitionController {

    /** {@code decisions} — ответы на решения главы: код решения → код варианта; для «Отклонить» не нужны. */
    record TransitionRequest(
            @NotNull Action action,
            @ApiNullable Map<String, String> decisions,
            @NotNull Integer expectedVersion) {
    }

    private final ChapterTransitionService transitions;

    ChapterTransitionController(ChapterTransitionService transitions) {
        this.transitions = transitions;
    }

    @Operation(operationId = "getChapterTransition")
    @GetMapping
    TransitionPreview preview(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                              @Parameter(description = "Ответы на решения главы: КОД_РЕШЕНИЯ:КОД_ВАРИАНТА, например TRAIN_WITH_VOLTYAR:YES")
                              @RequestParam(name = "decision", required = false) List<String> decision) {
        return transitions.preview(principal, campaignId, decisions(decision));
    }

    @Operation(operationId = "submitChapterTransition")
    @PostMapping
    CampaignSheet submit(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                         @Valid @RequestBody TransitionRequest body) {
        Map<String, String> decisions = body.decisions() == null ? Map.of() : body.decisions();
        return transitions.submit(principal, campaignId, body.action(), decisions, body.expectedVersion());
    }

    private static Map<String, String> decisions(List<String> answers) {
        Map<String, String> decisions = new LinkedHashMap<>();
        for (String answer : answers == null ? List.<String>of() : answers) {
            int colon = answer.indexOf(':');
            if (colon <= 0 || colon == answer.length() - 1) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Ответ на решение — КОД:ВАРИАНТ, а не «" + answer + "».");
            }
            decisions.put(answer.substring(0, colon), answer.substring(colon + 1));
        }
        return decisions;
    }
}
