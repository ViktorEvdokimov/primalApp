package com.primal.access;

import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.GuestDeviceAttached;
import com.primal.identity.PrimalPrincipal;
import com.primal.identity.PrimalPrincipal.GuestPrincipal;
import com.primal.identity.PrimalPrincipal.UserPrincipal;
import com.primal.rules.model.HunterClass;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Основной персонаж участника кампании ({@code doc/api.md} §5.2, qa № 150): охотник отряда, выбранный по умолчанию
 * в листе, кузнице и лаборатории. Свой у каждого участника: у пользователя — на всех его устройствах, у гостя — на
 * этом устройстве. Выбирается в листе кампании или при входе по ссылке.
 */
@Service
public class MainHunterService {

    private final AccessService access;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    MainHunterService(AccessService access, JdbcTemplate jdbc, Clock clock) {
        this.access = access;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Основной персонаж участника; доступ проверяет вызывающий. Охотника нет в отряде — пусто. */
    @Transactional(readOnly = true)
    public Optional<HunterClass> get(PrimalPrincipal principal, long campaignId) {
        return Optional.ofNullable(mainHunters(principal, List.of(campaignId)).get(campaignId));
    }

    /** Основные персонажи участника в кампаниях списка — одним запросом. */
    @Transactional(readOnly = true)
    public Map<Long, HunterClass> mainHunters(PrimalPrincipal principal, List<Long> campaignIds) {
        Map<Long, HunterClass> found = new HashMap<>();
        if (campaignIds.isEmpty()) {
            return found;
        }
        String ids = String.join(",", campaignIds.stream().map(String::valueOf).toList());
        String owner = principal instanceof UserPrincipal ? "m.user_id = ?" : "m.device_id = ?";
        jdbc.query("""
                select m.campaign_id, m.hunter_class from campaign_main_hunter m
                join campaign_hunter h on h.campaign_id = m.campaign_id and h.hunter_class = m.hunter_class
                where m.campaign_id in (%s) and %s""".formatted(ids, owner),
                rs -> {
                    found.put(rs.getLong(1), HunterClass.valueOf(rs.getString(2)));
                }, ownerKey(principal));
        return found;
    }

    /** Выбрать основного персонажа ({@code null} — снять выбор). Охотника нет в отряде — {@code 400}. */
    @Transactional
    public Optional<HunterClass> set(PrimalPrincipal principal, long campaignId, HunterClass hunterClass) {
        access.require(principal, campaignId);
        Long userId = principal instanceof UserPrincipal user ? user.userId() : null;
        UUID deviceId = principal instanceof GuestPrincipal guest ? guest.deviceId() : null;
        save(campaignId, userId, deviceId, hunterClass);
        return Optional.ofNullable(hunterClass);
    }

    /**
     * Запись без проверки доступа — для входа по ссылке ({@link ShareLinkService#join}): ровно один из
     * {@code userId}, {@code deviceId}.
     */
    void save(long campaignId, Long userId, UUID deviceId, HunterClass hunterClass) {
        String owner = userId != null ? "user_id" : "device_id";
        Object key = userId != null ? userId : deviceId;
        if (hunterClass == null) {
            jdbc.update("delete from campaign_main_hunter where campaign_id = ? and " + owner + " = ?", campaignId, key);
            return;
        }
        requireInSquad(campaignId, hunterClass);
        jdbc.update("""
                insert into campaign_main_hunter (campaign_id, %1$s, hunter_class, updated_at) values (?, ?, ?, ?)
                on conflict (campaign_id, %1$s) where %1$s is not null
                do update set hunter_class = excluded.hunter_class, updated_at = excluded.updated_at""".formatted(owner),
                campaignId, key, hunterClass.name(), Timestamp.from(clock.instant()));
    }

    /** Имя игрока охотника отряда; охотника нет — {@code 400} у поля {@code hunterClass}. */
    String playerName(long campaignId, HunterClass hunterClass) {
        return jdbc.queryForList("select player_name from campaign_hunter where campaign_id = ? and hunter_class = ?",
                        String.class, campaignId, hunterClass.name())
                .stream().findFirst()
                .orElseThrow(() -> notInSquad(hunterClass));
    }

    /** Гость вошёл в аккаунт: его выбор переходит аккаунту, если у аккаунта в этой кампании выбора нет. */
    @EventListener
    public void onGuestAttached(GuestDeviceAttached event) {
        jdbc.update("""
                insert into campaign_main_hunter (campaign_id, user_id, hunter_class, updated_at)
                select campaign_id, ?, hunter_class, updated_at from campaign_main_hunter where device_id = ?
                on conflict (campaign_id, user_id) where user_id is not null do nothing""",
                event.userId(), event.deviceId());
        jdbc.update("delete from campaign_main_hunter where device_id = ?", event.deviceId());
    }

    private void requireInSquad(long campaignId, HunterClass hunterClass) {
        playerName(campaignId, hunterClass);
    }

    private static Object ownerKey(PrimalPrincipal principal) {
        return principal instanceof UserPrincipal user ? user.userId() : principal.deviceId();
    }

    private static ApiException notInSquad(HunterClass hunterClass) {
        String message = "Охотника «" + hunterClass.displayName() + "» нет в отряде кампании.";
        return new ApiException(ErrorCode.VALIDATION_FAILED, message)
                .with("errors", List.of(Map.of("field", "hunterClass", "message", message)));
    }
}
