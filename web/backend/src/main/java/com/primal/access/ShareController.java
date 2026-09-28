package com.primal.access;

import com.primal.access.ShareLinkService.Joined;
import com.primal.access.ShareLinkService.LinkView;
import com.primal.common.api.ApiNullable;
import com.primal.identity.DeviceCookies;
import com.primal.identity.PrimalPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Ссылки-приглашения: управление владельцем и вход по ссылке ({@code doc/api.md} §9.1–9.2). */
@Tag(name = "sharing", description = "Совместная игра")
@RestController
class ShareController {

    public enum ParticipantKind { USER, GUEST }

    /** Чем делятся: пока только кампанией (экспедиция на сервере не хранится). */
    public enum InvitationKind { CAMPAIGN }

    record ParticipantView(ParticipantKind kind, String name, Instant joinedAt) {
    }

    record ShareLinkView(String url, Instant createdAt, List<ParticipantView> joined) {
    }

    record InvitationView(InvitationKind kind, String name, String ownerName) {
    }

    /** {@code displayName} — имя гостя в истории боёв; необязательно. */
    record JoinRequest(@ApiNullable @Size(max = 60, message = "Имя — не длиннее 60 символов") String displayName) {
    }

    record JoinResponse(InvitationKind kind, long id) {
    }

    private final ShareLinkService links;
    private final DeviceCookies cookies;

    ShareController(ShareLinkService links, DeviceCookies cookies) {
        this.links = links;
        this.cookies = cookies;
    }

    @Operation(operationId = "createShareLink")
    @ApiResponse(responseCode = "201", description = "Ссылка создана; прежняя, если была, отозвана")
    @PostMapping("/api/v1/campaigns/{campaignId}/share-link")
    ResponseEntity<ShareLinkView> create(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId) {
        return ResponseEntity.created(URI.create("/api/v1/campaigns/" + campaignId + "/share-link"))
                .body(view(links.create(principal, campaignId)));
    }

    @Operation(operationId = "getShareLink")
    @GetMapping("/api/v1/campaigns/{campaignId}/share-link")
    ShareLinkView get(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId) {
        return view(links.get(principal, campaignId));
    }

    @Operation(operationId = "revokeShareLink")
    @ApiResponse(responseCode = "204", description = "Ссылка отозвана")
    @DeleteMapping("/api/v1/campaigns/{campaignId}/share-link")
    ResponseEntity<Void> revoke(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long campaignId) {
        links.revoke(principal, campaignId);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "getInvitation")
    @GetMapping("/api/v1/share/{token}")
    InvitationView invitation(@PathVariable String token) {
        ShareLinkService.Invitation invitation = links.invitation(token);
        return new InvitationView(InvitationKind.CAMPAIGN, invitation.name(), invitation.ownerName());
    }

    /** Без cookie создаётся гостевое устройство — ответ выдаёт его cookie {@code PRIMAL_DEVICE}. */
    @Operation(operationId = "joinShareLink")
    @PostMapping("/api/v1/share/{token}/join")
    ResponseEntity<JoinResponse> join(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable String token,
                                      @Valid @RequestBody(required = false) JoinRequest body, HttpServletRequest request) {
        Joined joined = links.join(principal, token, body == null ? null : body.displayName(),
                request.getHeader(HttpHeaders.USER_AGENT), clientIp(request));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (joined.newDevice() != null) {
            response.header(HttpHeaders.SET_COOKIE, cookies.issue(joined.newDevice().token()).toString());
        }
        return response.body(new JoinResponse(InvitationKind.CAMPAIGN, joined.campaignId()));
    }

    private static ShareLinkView view(LinkView link) {
        return new ShareLinkView(link.url(), link.createdAt(), link.joined().stream()
                .map(p -> new ParticipantView(p.guest() ? ParticipantKind.GUEST : ParticipantKind.USER, p.name(), p.joinedAt()))
                .toList());
    }

    /** Адрес клиента; за Caddy его подставляет {@code X-Forwarded-For} ({@code server.forward-headers-strategy}). */
    private static InetAddress clientIp(HttpServletRequest request) {
        try {
            return InetAddress.ofLiteral(request.getRemoteAddr());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
