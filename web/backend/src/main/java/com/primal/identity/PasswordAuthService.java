package com.primal.identity;

import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.common.ratelimit.RateLimiter;
import com.primal.common.ratelimit.RateLimiter.Limit;
import com.primal.identity.DeviceService.IssuedDevice;
import java.net.InetAddress;
import java.time.Clock;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Регистрация и вход по логину и паролю ({@code doc/api.md} §3). Логин — свободное поле, пароль хранится
 * только хешем (bcrypt). Браузер запоминается: гостевое устройство этого браузера становится устройством
 * пользователя.
 */
@Service
public class PasswordAuthService {

    /** Регистрация; пароль в {@link #toString()} не попадает. */
    public record Registration(String login, String password, String displayName) {

        @Override
        public String toString() {
            return "Registration[login=" + login + "]";
        }
    }

    public record SignIn(AppUser user, IssuedDevice device) {
    }

    private final AppUserRepository users;
    private final DeviceService devices;
    private final PasswordEncoder encoder;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    /** Хеш для сравнения, когда логина нет: по времени ответа не понять, зарегистрирован ли логин. */
    private final String missingUserHash;

    PasswordAuthService(AppUserRepository users, DeviceService devices, PasswordEncoder encoder,
                        RateLimiter rateLimiter, Clock clock) {
        this.users = users;
        this.devices = devices;
        this.encoder = encoder;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.missingUserHash = encoder.encode("missing-user-password");
    }

    @Transactional
    public SignIn register(Registration registration, InetAddress ip, Optional<String> currentDeviceToken,
                           String userAgent) {
        rateLimiter.check(Limit.REGISTER_PER_IP, ipKey(ip));
        String login = Credentials.normalizeLogin(registration.login());
        checkPasswordLength(registration.password(), "password");
        if (users.existsByLogin(login)) {
            throw loginTaken(login);
        }
        AppUser user;
        try {
            user = users.saveAndFlush(new AppUser(login, encoder.encode(registration.password()),
                    registration.displayName().strip(), clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            throw loginTaken(login); // тот же логин зарегистрировали одновременно
        }
        return new SignIn(user, devices.signIn(user.getId(), currentDeviceToken, userAgent));
    }

    /** Незарегистрированный логин и неверный пароль неразличимы — ни по ответу, ни по времени. */
    @Transactional
    public SignIn login(String login, String password, InetAddress ip, Optional<String> currentDeviceToken,
                        String userAgent) {
        rateLimiter.check(Limit.LOGIN_PER_IP, ipKey(ip));
        String normalized = Credentials.normalizeLogin(login);
        rateLimiter.check(Limit.LOGIN_PER_ACCOUNT, normalized);
        Optional<AppUser> user = users.findByLogin(normalized).filter(account -> account.getPasswordHash() != null);
        boolean matches = Credentials.passwordFitsHash(password)
                && encoder.matches(password, user.map(AppUser::getPasswordHash).orElse(missingUserHash))
                && user.isPresent();
        if (!matches) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Неверный логин или пароль.");
        }
        return new SignIn(user.get(), devices.signIn(user.get().getId(), currentDeviceToken, userAgent));
    }

    /** Длина в символах проверяется аннотацией запроса, здесь — в байтах: bcrypt учитывает только 72. */
    static void checkPasswordLength(String password, String field) {
        if (!Credentials.passwordFitsHash(password)) {
            throw Credentials.invalidField(field, "Пароль слишком длинный: сократите его");
        }
    }

    static ApiException loginTaken(String login) {
        return new ApiException(ErrorCode.LOGIN_TAKEN, "Логин " + login + " уже зарегистрирован. Войдите по нему.");
    }

    private static String ipKey(InetAddress ip) {
        return ip == null ? "unknown" : ip.getHostAddress();
    }
}
