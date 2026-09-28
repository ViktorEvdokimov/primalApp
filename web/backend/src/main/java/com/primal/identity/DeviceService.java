package com.primal.identity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal.GuestPrincipal;
import com.primal.identity.PrimalPrincipal.UserPrincipal;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Устройства — запомненные браузеры ({@code doc/architecture.md} §6). Запрос аутентифицируется по хешу
 * токена из cookie; найденное устройство кэшируется на минуту, отзыв сбрасывает кэш сразу.
 */
@Service
public class DeviceService {

    /** Устройство без визитов дольше года отключается. */
    static final Duration IDLE_LIMIT = Duration.ofDays(365);
    /** Cookie продлевается, если с прошлой выдачи прошло больше 30 дней (браузеры хранят её до 400 дней). */
    static final Duration COOKIE_RENEWAL = Duration.ofDays(30);
    /** Время визита пишется в БД не чаще раза в час. */
    static final Duration LAST_SEEN_STEP = Duration.ofHours(1);

    /** Выданное устройство; {@code token} уходит только в cookie. */
    public record IssuedDevice(UUID id, String userAgent, String token) {

        @Override
        public String toString() {
            return "IssuedDevice[id=" + id + ", userAgent=" + userAgent + "]";
        }
    }

    /** Результат аутентификации: кто пришёл и нужно ли продлить cookie. */
    public record Authentication(PrimalPrincipal principal, boolean renewCookie) {
    }

    /** Устройство в списке «Мои устройства». */
    public record DeviceSummary(UUID id, String userAgent, Instant createdAt, Instant lastSeenAt, boolean current) {
    }

    private record CachedDevice(UUID id, Long userId, Instant lastSeenAt, Instant cookieRenewedAt) {

        static CachedDevice of(Device device) {
            return new CachedDevice(device.getId(), device.getUserId(), device.getLastSeenAt(), device.getCookieRenewedAt());
        }

        CachedDevice touched(Instant now, boolean cookieRenewed) {
            return new CachedDevice(id, userId, now, cookieRenewed ? now : cookieRenewedAt);
        }

        PrimalPrincipal principal() {
            return userId == null ? new GuestPrincipal(id) : new UserPrincipal(userId, id);
        }
    }

    private final DeviceRepository devices;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Cache<ByteBuffer, CachedDevice> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(10_000)
            .build();

    DeviceService(DeviceRepository devices, ApplicationEventPublisher events, Clock clock) {
        this.devices = devices;
        this.events = events;
        this.clock = clock;
    }

    /** Устройство по токену из cookie; пусто — токен неизвестен, устройство отозвано или простаивало больше года. */
    public Optional<Authentication> authenticate(String token) {
        ByteBuffer key = ByteBuffer.wrap(Tokens.sha256(token));
        CachedDevice device = cache.getIfPresent(key);
        if (device == null) {
            device = devices.findByTokenHash(key.array()).filter(found -> !found.isRevoked()).map(CachedDevice::of).orElse(null);
            if (device == null) {
                return Optional.empty();
            }
        }
        Instant now = clock.instant();
        if (device.lastSeenAt().plus(IDLE_LIMIT).isBefore(now)) {
            cache.invalidate(key);
            return Optional.empty();
        }
        boolean renewCookie = device.cookieRenewedAt().plus(COOKIE_RENEWAL).isBefore(now);
        if (renewCookie || device.lastSeenAt().plus(LAST_SEEN_STEP).isBefore(now)) {
            devices.touch(device.id(), now, renewCookie);
            device = device.touched(now, renewCookie);
        }
        cache.put(key, device);
        return Optional.of(new Authentication(device.principal(), renewCookie));
    }

    /**
     * Вход пользователя в этом браузере. Гостевое устройство (открывали ссылку-приглашение) привязывается к
     * аккаунту, токен остаётся прежним. Устройство прежнего входа в этом браузере отзывается: cookie заменяется.
     */
    @Transactional
    public IssuedDevice signIn(long userId, Optional<String> currentToken, String userAgentHeader) {
        Instant now = clock.instant();
        String userAgent = UserAgents.shortName(userAgentHeader);
        Optional<Device> current = currentToken
                .flatMap(token -> devices.findByTokenHash(Tokens.sha256(token)))
                .filter(device -> !device.isRevoked());
        current.ifPresent(device -> evict(device.getId()));
        if (current.isPresent() && current.get().isGuest()) {
            Device guest = current.get();
            guest.attachTo(userId, userAgent, now);
            events.publishEvent(new GuestDeviceAttached(guest.getId(), userId));
            return new IssuedDevice(guest.getId(), guest.getUserAgent(), currentToken.orElseThrow());
        }
        current.ifPresent(device -> {
            device.revoke(now);
            events.publishEvent(new DevicesRevoked(List.of(device.getId())));
        });
        String token = Tokens.newDeviceToken();
        Device device = devices.save(new Device(UUID.randomUUID(), userId, Tokens.sha256(token), userAgent, now));
        return new IssuedDevice(device.getId(), device.getUserAgent(), token);
    }

    /** Гостевое устройство — открыли ссылку-приглашение без входа; {@code displayName} — имя в истории боёв. */
    @Transactional
    public IssuedDevice createGuest(String userAgentHeader, String displayName) {
        Instant now = clock.instant();
        String token = Tokens.newDeviceToken();
        Device device = new Device(UUID.randomUUID(), null, Tokens.sha256(token), UserAgents.shortName(userAgentHeader), now);
        device.rename(displayName);
        devices.saveAndFlush(device); // доступ по ссылке записывается сразу следом, SQL-запросом
        return new IssuedDevice(device.getId(), device.getUserAgent(), token);
    }

    @Transactional(readOnly = true)
    public List<DeviceSummary> list(UserPrincipal principal) {
        return devices.findActiveByUser(principal.userId()).stream()
                .map(device -> new DeviceSummary(device.getId(), device.getUserAgent(), device.getCreatedAt(),
                        device.getLastSeenAt(), device.getId().equals(principal.deviceId())))
                .toList();
    }

    /** Отзыв своего устройства; чужое или уже отозванное — {@code 404}. */
    @Transactional
    public void revoke(UserPrincipal principal, UUID deviceId) {
        Device device = devices.findById(deviceId)
                .filter(found -> !found.isRevoked() && Long.valueOf(principal.userId()).equals(found.getUserId()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Устройство не найдено."));
        device.revoke(clock.instant());
        evict(deviceId);
        events.publishEvent(new DevicesRevoked(List.of(deviceId)));
    }

    /** «Выйти на всех других устройствах». */
    @Transactional
    public void revokeOthers(UserPrincipal principal) {
        List<UUID> others = devices.findActiveByUser(principal.userId()).stream()
                .map(Device::getId)
                .filter(id -> !id.equals(principal.deviceId()))
                .toList();
        devices.revokeOthers(principal.userId(), principal.deviceId(), clock.instant());
        others.forEach(this::evict);
        events.publishEvent(new DevicesRevoked(others));
    }

    /** «Выйти»: отзывается текущее устройство — и пользователя, и гостя. */
    @Transactional
    public void logout(PrimalPrincipal principal) {
        devices.revoke(principal.deviceId(), clock.instant());
        evict(principal.deviceId());
        events.publishEvent(new DevicesRevoked(List.of(principal.deviceId())));
    }

    @Transactional(readOnly = true)
    public Optional<String> displayName(UUID deviceId) {
        return devices.findById(deviceId).map(Device::getDisplayName);
    }

    /** Чьё устройство: {@code userId} — у устройства пользователя, {@code null} — у гостя. */
    record Owner(Long userId, String displayName) {
    }

    @Transactional(readOnly = true)
    Optional<Owner> owner(UUID deviceId) {
        return devices.findById(deviceId).map(device -> new Owner(device.getUserId(), device.getDisplayName()));
    }

    /** Имя гостя в истории боёв кампании. */
    @Transactional
    public void rename(UUID deviceId, String displayName) {
        devices.findById(deviceId).ifPresent(device -> device.rename(displayName));
    }

    private void evict(UUID deviceId) {
        cache.asMap().values().removeIf(cached -> cached.id().equals(deviceId));
    }
}
