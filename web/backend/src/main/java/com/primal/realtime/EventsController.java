package com.primal.realtime;

import com.primal.access.AccessService;
import com.primal.identity.PrimalPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Поток изменений кампании ({@code doc/api.md} §9.3): {@code campaign.updated} с новой версией и автором,
 * {@code access.revoked} при потере доступа; пульс — комментарий каждые 25 секунд.
 */
@Tag(name = "events", description = "Обновления в реальном времени")
@RestController
class EventsController {

    private final AccessService access;
    private final EventHub hub;

    EventsController(AccessService access, EventHub hub) {
        this.access = access;
        this.hub = hub;
    }

    @Operation(operationId = "campaignEvents")
    @GetMapping(path = "/api/v1/campaigns/{campaignId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId) {
        access.require(principal, campaignId);
        return hub.subscribe(campaignId, principal);
    }
}
