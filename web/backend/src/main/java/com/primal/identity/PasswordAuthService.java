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
 * Регистрация и вход по номеру телефона и паролю ({@code doc/api.md} §3). Номер — логин, пароль хранится
 * только хешем (bcrypt). Браузер запоминается, как и раньше: гостевое устройство этого браузера становится
 * устройством пользователя.
 */
@Service
public class PasswordAuthService {

    /** Регистрация; пароль в {@link #toString()} не попадает. */
    public record Registration(String phone, String password, String displayName) {

        @Override
        public String toString() {
            return "Registration[phone=" + phone + "]";
        }
    }

    public record SignIn(AppUser user, IssuedDevice device) {
    }

    private final AppUserRepository users;
    private final DeviceService devices;
    private final PasswordEncoder encoder;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    /** Хеш для сравнения, когда номера нет: по времени ответа не понять, зарегистрирован ли номер. */
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
        String phone = Credentials.normalizePhone(registration.phone());
        checkPasswordLength(registration.password(), "password");
        if (users.existsByPhone(phone)) {
            throw phoneTaken(phone);
        }
        AppUser user;
        try {
            user = users.saveAndFlush(new AppUser(phone, encoder.encode(registration.password()),
                    registration.displayName().strip(), clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            throw phoneTaken(phone); // тот же номер зарегистрировали одновременно
        }
        return new SignIn(user, devices.signIn(user.getId(), currentDeviceToken, userAgent));
    }

    /** Незарегистрированный номер и неверный пароль неразличимы — ни по ответу, ни по времени. */
    @Transactional
    public SignIn login(String phone, String password, InetAddress ip, Optional<String> currentDeviceToken,
                        String userAgent) {
        rateLimiter.check(Limit.LOGIN_PER_IP, ipKey(ip));
        String normalized = Credentials.normalizePhone(phone);
        rateLimiter.check(Limit.LOGIN_PER_ACCOUNT, normalized);
        Optional<AppUser> user = users.findByPhone(normalized).filter(account -> account.getPasswordHash() != null);
        boolean matches = Credentials.passwordFitsHash(password)
                && encoder.matches(password, user.map(AppUser::getPasswordHash).orElse(missingUserHash))
                && user.isPresent();
        if (!matches) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Неверный номер телефона или пароль.");
        }
        return new SignIn(user.get(), devices.signIn(user.get().getId(), currentDeviceToken, userAgent));
    }

    /** Длина в символах проверяется аннотацией запроса, здесь — в байтах: bcrypt учитывает только 72. */
    static void checkPasswordLength(String password, String field) {
        if (!Credentials.passwordFitsHash(password)) {
            throw Credentials.invalidField(field, "Пароль слишком длинный: сократите его");
        }
    }

    static ApiException phoneTaken(String phone) {
        return new ApiException(ErrorCode.PHONE_TAKEN, "Номер " + phone + " уже зарегистрирован. Войдите по нему.");
    }

    private static String ipKey(InetAddress ip) {
        return ip == null ? "unknown" : ip.getHostAddress();
    }
}
