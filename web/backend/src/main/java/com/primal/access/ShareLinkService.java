package com.primal.access;

import com.primal.common.config.PrimalProperties;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.common.ratelimit.RateLimiter;
import com.primal.identity.AccountService;
import com.primal.identity.AccountService.Author;
import com.primal.identity.DeviceService;
import com.primal.identity.DeviceService.IssuedDevice;
import com.primal.identity.GuestDeviceAttached;
import com.primal.identity.PrimalPrincipal;
import com.primal.identity.PrimalPrincipal.GuestPrincipal;
import com.primal.identity.PrimalPrincipal.UserPrincipal;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ссылки-приглашения на кампании ({@code doc/api.md} §9.1–9.2): владелец создаёт, перевыпускает и отзывает
 * ссылку; кто её открыл — получает доступ к кампании с правом редактирования.
 */
@Service
public class ShareLinkService {

    /** Кто открыл ссылку: гость — по имени устройства, пользователь — по имени аккаунта. */
    public record Participant(boolean guest, String name, Instant joinedAt) {
    }

    /** Ссылка для окна «Поделиться». */
    public record LinkView(String url, Instant createdAt, List<Participant> joined) {
    }

    /** Что за приглашение — показывается до входа. */
    public record Invitation(long campaignId, String name, String ownerName) {
    }

    /** Итог входа по ссылке; {@code newDevice} — гостевое устройство, если cookie не было. */
    public record Joined(long campaignId, IssuedDevice newDevice) {
    }

    private final ShareLinkRepository links;
    private final ShareAccessRepository accesses;
    private final ShareTokenCodec codec;
    private final AccessService access;
    private final AccountService accounts;
    private final DeviceService devices;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;
    private final String publicUrl;
    private final Clock clock;

    ShareLinkService(ShareLinkRepository links, ShareAccessRepository accesses, ShareTokenCodec codec, AccessService access,
                     AccountService accounts, DeviceService devices, RateLimiter rateLimiter,
                     ApplicationEventPublisher events, JdbcTemplate jdbc, PrimalProperties properties, Clock clock) {
        this.links = links;
        this.accesses = accesses;
        this.codec = codec;
        this.access = access;
        this.accounts = accounts;
        this.devices = devices;
        this.rateLimiter = rateLimiter;
        this.events = events;
        this.jdbc = jdbc;
        this.publicUrl = properties.publicUrl().replaceAll("/+$", "");
        this.clock = clock;
    }

    /** Создать ссылку; если она уже есть — перевыпустить: старая отзывается, доступы по ней пропадают сразу. */
    @Transactional
    public LinkView create(PrimalPrincipal principal, long campaignId) {
        access.requireOwner(principal, campaignId);
        Instant now = clock.instant();
        links.findFirstByCampaignIdAndRevokedAtIsNull(campaignId).ifPresent(old -> {
            old.revoke(now);
            links.saveAndFlush(old);
            events.publishEvent(new ShareLinkChanged(campaignId));
        });
        long owner = ((UserPrincipal) principal).userId();
        return view(links.saveAndFlush(new ShareLink(UUID.randomUUID(), campaignId, owner, now)));
    }

    /** Действующая ссылка; нет — {@code 404}. */
    @Transactional(readOnly = true)
    public LinkView get(PrimalPrincipal principal, long campaignId) {
        access.requireOwner(principal, campaignId);
        return view(links.findFirstByCampaignIdAndRevokedAtIsNull(campaignId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Ссылки на кампанию нет.")));
    }

    /** Отозвать: доступ по ссылке сразу пропадает у всех. */
    @Transactional
    public void revoke(PrimalPrincipal principal, long campaignId) {
        access.requireOwner(principal, campaignId);
        links.findFirstByCampaignIdAndRevokedAtIsNull(campaignId).ifPresent(link -> {
            link.revoke(clock.instant());
            events.publishEvent(new ShareLinkChanged(campaignId));
        });
    }

    @Transactional(readOnly = true)
    public Invitation invitation(String token) {
        ShareLink link = active(token);
        Map<String, Object> campaign = jdbc.queryForMap("select name, owner_id from campaign where id = ?", link.getCampaignId());
        return new Invitation(link.getCampaignId(), (String) campaign.get("name"),
                accounts.userName(((Number) campaign.get("owner_id")).longValue()));
    }

    /**
     * Вход по ссылке: без cookie — новое гостевое устройство; гость — доступ на устройство (и имя в истории
     * боёв); пользователь — на аккаунт; владелец ничего не получает. Повтор ничего не дублирует.
     */
    @Transactional
    public Joined join(PrimalPrincipal principal, String token, String displayName, String userAgent, InetAddress ip) {
        rateLimiter.check(RateLimiter.Limit.JOIN_PER_IP, ip == null ? "unknown" : ip.getHostAddress());
        ShareLink link = active(token);
        long campaignId = link.getCampaignId();
        String name = displayName == null || displayName.isBlank() ? null : displayName.strip();
        switch (principal) {
            case null -> {
                IssuedDevice device = devices.createGuest(userAgent, name);
                grant(link.getId(), null, device.id());
                return new Joined(campaignId, device);
            }
            case GuestPrincipal guest -> {
                if (name != null) {
                    accounts.rename(guest, name);
                }
                grant(link.getId(), null, guest.deviceId());
            }
            case UserPrincipal user -> {
                boolean owner = Long.valueOf(user.userId()).equals(
                        jdbc.queryForObject("select owner_id from campaign where id = ?", Long.class, campaignId));
                if (!owner) {
                    grant(link.getId(), user.userId(), null);
                }
            }
        }
        return new Joined(campaignId, null);
    }

    /** Гость вошёл по коду: его доступы по ссылкам переходят аккаунту, дубликаты удаляются ({@code data-model.md} §3.5). */
    @EventListener
    public void onGuestAttached(GuestDeviceAttached event) {
        jdbc.update("""
                insert into share_access (share_link_id, user_id, joined_at)
                select share_link_id, ?, joined_at from share_access where device_id = ?
                on conflict (share_link_id, user_id) where user_id is not null do nothing""",
                event.userId(), event.deviceId());
        jdbc.update("delete from share_access where device_id = ?", event.deviceId());
    }

    private void grant(UUID linkId, Long userId, UUID deviceId) {
        String target = userId != null ? "(share_link_id, user_id) where user_id is not null"
                : "(share_link_id, device_id) where device_id is not null";
        jdbc.update("insert into share_access (share_link_id, user_id, device_id, joined_at) values (?, ?, ?, ?) "
                + "on conflict " + target + " do nothing", linkId, userId, deviceId, Timestamp.from(clock.instant()));
    }

    /** Действующая ссылка по токену; подделанный, отозванный или перевыпущенный токен — {@code 404}. */
    private ShareLink active(String token) {
        return codec.decode(token)
                .flatMap(links::findById)
                .filter(link -> !link.isRevoked())
                .orElseThrow(() -> new ApiException(ErrorCode.SHARE_LINK_INVALID,
                        "Ссылка недействительна: её отозвали или перевыпустили. Попросите у владельца новую."));
    }

    private LinkView view(ShareLink link) {
        List<Participant> joined = accesses.findByShareLinkIdOrderByJoinedAt(link.getId()).stream()
                .map(participant -> {
                    if (participant.getUserId() != null) {
                        return new Participant(false, accounts.userName(participant.getUserId()), participant.getJoinedAt());
                    }
                    Author author = accounts.author(participant.getDeviceId());
                    return new Participant(true, author.name(), participant.getJoinedAt());
                })
                .toList();
        return new LinkView(publicUrl + "/s/" + codec.encode(link.getId()), link.getCreatedAt(), joined);
    }
}
