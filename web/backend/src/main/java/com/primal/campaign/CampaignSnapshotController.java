package com.primal.campaign;

import com.primal.campaign.CampaignSnapshotDto.CampaignSnapshot;
import com.primal.campaign.CampaignSnapshotDto.SnapshotImport;
import com.primal.campaign.CampaignSnapshotDto.SnapshotUpdate;
import com.primal.identity.PrimalPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Синхронизация мобильного приложения ({@code doc/api.md} §11): снимок кампании целиком. ETag снимка — его версия:
 * повторный запрос с {@code If-None-Match} получает {@code 304}, если кампания не менялась.
 */
@Tag(name = "campaigns", description = "Кампании")
@RestController
@RequestMapping("/api/v1/campaigns")
class CampaignSnapshotController {

    private final CampaignSnapshots snapshots;

    CampaignSnapshotController(CampaignSnapshots snapshots) {
        this.snapshots = snapshots;
    }

    @Operation(operationId = "getCampaignSnapshot")
    @GetMapping("/{id}/snapshot")
    ResponseEntity<CampaignSnapshot> get(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long id) {
        CampaignSnapshot snapshot = snapshots.read(principal, id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .eTag("v" + snapshot.version())
                .body(snapshot);
    }

    @Operation(operationId = "putCampaignSnapshot")
    @PutMapping("/{id}/snapshot")
    CampaignSnapshot put(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long id,
                         @RequestBody SnapshotUpdate body) {
        return snapshots.replace(principal, id, body.expectedVersion(), body.state());
    }

    @Operation(operationId = "importCampaign")
    @ApiResponse(responseCode = "201", description = "Кампания загружена на сервер")
    @PostMapping("/import")
    ResponseEntity<CampaignSnapshot> importCampaign(@AuthenticationPrincipal PrimalPrincipal principal,
                                                    @RequestBody SnapshotImport body) {
        CampaignSnapshot snapshot = snapshots.importCampaign(principal, body.state());
        return ResponseEntity.created(URI.create("/api/v1/campaigns/" + snapshot.id())).body(snapshot);
    }
}
