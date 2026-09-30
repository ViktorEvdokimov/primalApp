package com.primal.identity;

import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.common.ratelimit.RateLimiter;
import com.primal.common.ratelimit.RateLimiter.Limit;
import com.primal.identity.PrimalPrincipal.GuestPrincipal;
import com.primal.identity.PrimalPrincipal.UserPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * «Кто я», имя, телефон и пароль ({@code doc/api.md} §3): у пользователя — имя аккаунта, у гостя — имя
 * устройства. Телефон (он же логин) и пароль есть только у аккаунта.
 */
@Service
public class AccountService {

    /** {@code user} есть только у пользователя, {@code deviceName} — только у гостя. */
    public record Me(PrimalPrincipal principal, Optional<AppUser> user, String deviceName) {
    }

    /** Кто начал бой или отправил результат: пользователь — по имени аккаунта, гость — по имени устройства. */
    public record Author(boolean guest, String name) {
    }

    /** Имя гостя без имени и автора, чьё устройство удалено. */
    static final String GUEST_NAME = "Гость";
    /** Имя пользователя без имени (аккаунт по почте): телефон другим участникам не показывается. */
    static final String PLAYER_NAME = "Игрок";
    static final String UNKNOWN_NAME = "—";

    private final AppUserRepository users;
    private final DeviceService devices;
    private final PasswordEncoder encoder;
    private final RateLimiter rateLimiter;

    AccountService(AppUserRepository users, DeviceService devices, PasswordEncoder encoder, RateLimiter rateLimiter) {
        this.users = users;
        this.devices = devices;
        this.encoder = encoder;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public Me me(PrimalPrincipal principal) {
        return switch (principal) {
            case UserPrincipal user -> new Me(principal, users.findById(user.userId()), null);
            case GuestPrincipal guest -> new Me(principal, Optional.empty(), devices.displayName(guest.deviceId()).orElse(null));
        };
    }

    /** Имя пользователя для других участников: своё имя или «Игрок» — номер телефона не показывается. */
    @Transactional(readOnly = true)
    public String userName(long userId) {
        return users.findById(userId)
                .map(user -> user.getDisplayName() != null ? user.getDisplayName() : PLAYER_NAME)
                .orElse(UNKNOWN_NAME);
    }

    /** Автор действия с устройства; устройство удалено ({@code null}) — «—». */
    @Transactional(readOnly = true)
    public Author author(UUID deviceId) {
        if (deviceId == null) {
            return new Author(true, UNKNOWN_NAME);
        }
        return devices.owner(deviceId)
                .map(owner -> owner.userId() != null
                        ? new Author(false, userName(owner.userId()))
                        : new Author(true, owner.displayName() != null ? owner.displayName() : GUEST_NAME))
                .orElse(new Author(true, UNKNOWN_NAME));
    }

    /** Пустое имя сбрасывает его: показывается «Игрок» или «Гость». */
    @Transactional
    public Me rename(PrimalPrincipal principal, String displayName) {
        String name = displayName == null || displayName.isBlank() ? null : displayName.strip();
        switch (principal) {
            case UserPrincipal user -> users.findById(user.userId()).ifPresent(account -> account.setDisplayName(name));
            case GuestPrincipal guest -> devices.rename(guest.deviceId(), name);
        }
        return me(principal);
    }

    /** Новый номер — новый логин; занятый другим аккаунтом — {@code 409 PHONE_TAKEN}. */
    @Transactional
    public Me changePhone(PrimalPrincipal principal, String phone) {
        String normalized = Credentials.normalizePhone(phone);
        AppUser user = account(principal);
        if (!normalized.equals(user.getPhone()) && users.existsByPhone(normalized)) {
            throw PasswordAuthService.phoneTaken(normalized);
        }
        user.setPhone(normalized);
        try {
            users.flush(); // тот же номер одновременно у двух аккаунтов упрётся в уникальный индекс здесь
        } catch (DataIntegrityViolationException exception) {
            throw PasswordAuthService.phoneTaken(normalized);
        }
        return me(principal);
    }

    /**
     * Новый пароль — после проверки текущего. У аккаунта без пароля (создан по почте) текущий не спрашивается:
     * запрос и так пришёл с его запомненного устройства. Попытки считаются вместе со входом по этому логину.
     */
    @Transactional
    public void changePassword(PrimalPrincipal principal, String currentPassword, String newPassword) {
        AppUser user = account(principal);
        if (user.getPasswordHash() != null) {
            rateLimiter.check(Limit.LOGIN_PER_ACCOUNT, user.getPhone() != null ? user.getPhone() : "id:" + user.getId());
            boolean matches = currentPassword != null && Credentials.passwordFitsHash(currentPassword)
                    && encoder.matches(currentPassword, user.getPasswordHash());
            if (!matches) {
                throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Текущий пароль указан неверно.");
            }
        }
        PasswordAuthService.checkPasswordLength(newPassword, "newPassword");
        user.setPasswordHash(encoder.encode(newPassword));
    }

    private AppUser account(PrimalPrincipal principal) {
        if (!(principal instanceof UserPrincipal user)) {
            throw new ApiException(ErrorCode.ACCOUNT_REQUIRED,
                    "Телефон и пароль есть только у аккаунта. Зарегистрируйтесь.");
        }
        return users.findById(user.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Войдите, чтобы продолжить."));
    }
}
