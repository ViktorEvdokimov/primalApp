package com.primal.access;

import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal;
import com.primal.identity.PrimalPrincipal.UserPrincipal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Права на кампании ({@code doc/architecture.md} §6, {@code doc/data-model.md} §3.5): владелец или открывший
 * действующую ссылку-приглашение — пользователь (на всех его устройствах) или гость (на этом устройстве).
 * Нет доступа — {@code 404}: существование чужой кампании не раскрывается.
 */
@Service
public class AccessService {

    public enum CampaignAccess { OWNER, LINK }

    /** Доступ по действующей ссылке: запись пользователя или устройства, ссылка не отозвана. */
    private static final String LINKED = """
            from share_access a join share_link l on l.id = a.share_link_id
            where l.revoked_at is null and (a.device_id = ? or a.user_id = ?)""";

    private final JdbcTemplate jdbc;

    AccessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Доступ к кампании; нет доступа или кампании — {@code 404 NOT_FOUND}. */
    public CampaignAccess require(PrimalPrincipal principal, long campaignId) {
        List<Long> owners = jdbc.queryForList("select owner_id from campaign where id = ?", Long.class, campaignId);
        if (owners.isEmpty()) {
            throw notFound();
        }
        if (principal instanceof UserPrincipal user && owners.getFirst() == user.userId()) {
            return CampaignAccess.OWNER;
        }
        Boolean linked = jdbc.queryForObject("select exists (select 1 " + LINKED + " and l.campaign_id = ?)",
                Boolean.class, principal.deviceId(), userId(principal), campaignId);
        if (Boolean.TRUE.equals(linked)) {
            return CampaignAccess.LINK;
        }
        throw notFound();
    }

    /** Действие только для владельца: удаление кампании, управление ссылкой ({@code 403 OWNER_ONLY}). */
    public void requireOwner(PrimalPrincipal principal, long campaignId) {
        if (require(principal, campaignId) != CampaignAccess.OWNER) {
            throw new ApiException(ErrorCode.OWNER_ONLY, "Это может сделать только владелец кампании.");
        }
    }

    /** Кампании, открытые по действующим ссылкам (без своих — их владелец видит и так). */
    public List<Long> linkedCampaignIds(PrimalPrincipal principal) {
        return jdbc.queryForList("select distinct l.campaign_id " + LINKED, Long.class, principal.deviceId(), userId(principal));
    }

    private static Long userId(PrimalPrincipal principal) {
        return principal instanceof UserPrincipal user ? user.userId() : null;
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "Кампания не найдена.");
    }
}
