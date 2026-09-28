package com.primal.identity;

import com.primal.common.config.PrimalProperties;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.common.ratelimit.RateLimiter;
import com.primal.common.ratelimit.RateLimiter.Limit;
import com.primal.identity.DeviceService.IssuedDevice;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Вход по коду из письма ({@code doc/api.md} §3): регистрация и вход — один сценарий. Код — 6 цифр,
 * действует 10 минут, 5 попыток; новый запрос отменяет прежний код.
 */
@Service
public class LoginCodeService {

    static final Duration CODE_VALIDITY = Duration.ofMinutes(10);
    static final Duration RESEND_DELAY = Duration.ofMinutes(1);
    static final int MAX_ATTEMPTS = 5;
    private static final Duration CHALLENGE_RETENTION = Duration.ofDays(1);

    public record CodeRequested(UUID challengeId, Instant expiresAt, Instant resendAfter) {
    }

    public record SignIn(AppUser user, boolean newUser, IssuedDevice device) {
    }

    private final LoginChallengeRepository challenges;
    private final AppUserRepository users;
    private final DeviceService devices;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final String pepper;

    LoginCodeService(LoginChallengeRepository challenges, AppUserRepository users, DeviceService devices,
                     RateLimiter rateLimiter, ApplicationEventPublisher events, Clock clock, PrimalProperties properties) {
        this.challenges = challenges;
        this.users = users;
        this.devices = devices;
        this.rateLimiter = rateLimiter;
        this.events = events;
        this.clock = clock;
        this.pepper = properties.otp().pepper();
    }

    /** Ответ одинаков для новых и известных адресов: существование пользователя не раскрывается. */
    @Transactional
    public CodeRequested requestCode(String email, InetAddress ip) {
        String normalized = normalize(email);
        rateLimiter.check(Limit.CODE_PER_IP, ipKey(ip));
        rateLimiter.check(Limit.CODE_PER_EMAIL, normalized);

        Instant now = clock.instant();
        challenges.consumeOpen(normalized, now);
        UUID id = UUID.randomUUID();
        String code = Tokens.newCode();
        challenges.save(new LoginChallenge(id, normalized, Tokens.codeHash(pepper, id, code), now.plus(CODE_VALIDITY),
                MAX_ATTEMPTS, ip, now));
        events.publishEvent(new LoginCodeRequested(normalized, code, CODE_VALIDITY));
        return new CodeRequested(id, now.plus(CODE_VALIDITY), now.plus(RESEND_DELAY));
    }

    /** Неверный код уменьшает число попыток — это изменение сохраняется, хотя ответ — ошибка. */
    @Transactional(noRollbackFor = ApiException.class)
    public SignIn verify(UUID challengeId, String code, InetAddress ip, Optional<String> currentDeviceToken,
                         String userAgent) {
        rateLimiter.check(Limit.VERIFY_PER_IP, ipKey(ip));
        Instant now = clock.instant();
        LoginChallenge challenge = challenges.lockById(challengeId)
                .filter(candidate -> candidate.isOpen(now))
                .orElseThrow(() -> new ApiException(ErrorCode.CODE_EXPIRED, "Код устарел. Запросите новый код."));
        if (!MessageDigest.isEqual(challenge.getCodeHash(), Tokens.codeHash(pepper, challengeId, code))) {
            challenge.failAttempt();
            throw new ApiException(ErrorCode.INVALID_CODE,
                    "Неверный код. Осталось попыток: " + challenge.getAttemptsLeft() + ".")
                    .with("attemptsLeft", challenge.getAttemptsLeft());
        }
        challenge.consume(now);

        Optional<AppUser> existing = users.findByEmail(challenge.getEmail());
        AppUser user = existing.orElseGet(() -> users.save(new AppUser(challenge.getEmail(), now)));
        IssuedDevice device = devices.signIn(user.getId(), currentDeviceToken, userAgent);
        return new SignIn(user, existing.isEmpty(), device);
    }

    /** Раз в сутки: запросы кодов старше суток больше не нужны. */
    @Scheduled(cron = "0 17 3 * * *", zone = "UTC")
    @Transactional
    public int deleteStaleChallenges() {
        return challenges.deleteCreatedBefore(clock.instant().minus(CHALLENGE_RETENTION));
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String ipKey(InetAddress ip) {
        return ip == null ? "unknown" : ip.getHostAddress();
    }
}
