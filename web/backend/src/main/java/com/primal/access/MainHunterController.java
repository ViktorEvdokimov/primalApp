package com.primal.access;

import com.primal.common.api.ApiNullable;
import com.primal.identity.PrimalPrincipal;
import com.primal.rules.model.HunterClass;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Основной персонаж участника кампании ({@code doc/api.md} §5.7). */
@Tag(name = "campaigns", description = "Кампании")
@RestController
class MainHunterController {

    /** {@code hunterClass} — охотник отряда; {@code null} — снять выбор. */
    record MainHunterRequest(@ApiNullable HunterClass hunterClass) {
    }

    record MainHunterView(@ApiNullable HunterClass mainHunter) {
    }

    private final MainHunterService mainHunters;

    MainHunterController(MainHunterService mainHunters) {
        this.mainHunters = mainHunters;
    }

    @Operation(operationId = "setMainHunter")
    @PutMapping("/api/v1/campaigns/{campaignId}/main-hunter")
    MainHunterView set(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId,
                       @RequestBody MainHunterRequest body) {
        return new MainHunterView(mainHunters.set(principal, campaignId, body.hunterClass()).orElse(null));
    }
}
