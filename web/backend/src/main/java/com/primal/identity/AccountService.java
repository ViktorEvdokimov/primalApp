package com.primal.identity;

import com.primal.identity.PrimalPrincipal.GuestPrincipal;
import com.primal.identity.PrimalPrincipal.UserPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** «Кто я» и имя: у пользователя — имя аккаунта, у гостя — имя устройства ({@code doc/api.md} §3). */
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
    static final String UNKNOWN_NAME = "—";

    private final AppUserRepository users;
    private final DeviceService devices;

    AccountService(AppUserRepository users, DeviceService devices) {
        this.users = users;
        this.devices = devices;
    }

    @Transactional(readOnly = true)
    public Me me(PrimalPrincipal principal) {
        return switch (principal) {
            case UserPrincipal user -> new Me(principal, users.findById(user.userId()), null);
            case GuestPrincipal guest -> new Me(principal, Optional.empty(), devices.displayName(guest.deviceId()).orElse(null));
        };
    }

    /** Имя пользователя для других участников: своё имя или часть почты до «@». */
    @Transactional(readOnly = true)
    public String userName(long userId) {
        return users.findById(userId)
                .map(user -> user.getDisplayName() != null ? user.getDisplayName() : user.getEmail().split("@")[0])
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

    /** Пустое имя сбрасывает его: показывается часть почты до «@» или «Гость». */
    @Transactional
    public Me rename(PrimalPrincipal principal, String displayName) {
        String name = displayName == null || displayName.isBlank() ? null : displayName.strip();
        switch (principal) {
            case UserPrincipal user -> users.findById(user.userId()).ifPresent(account -> account.setDisplayName(name));
            case GuestPrincipal guest -> devices.rename(guest.deviceId(), name);
        }
        return me(principal);
    }
}
