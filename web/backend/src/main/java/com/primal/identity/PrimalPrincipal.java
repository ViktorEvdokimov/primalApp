package com.primal.identity;

import java.util.UUID;

/**
 * Кто делает запрос ({@code doc/architecture.md} §6): пользователь с аккаунтом или гость по ссылке.
 * Определяется по cookie устройства; в контроллерах — {@code @AuthenticationPrincipal PrimalPrincipal}.
 */
public sealed interface PrimalPrincipal permits PrimalPrincipal.UserPrincipal, PrimalPrincipal.GuestPrincipal {

    UUID deviceId();

    record UserPrincipal(long userId, UUID deviceId) implements PrimalPrincipal {
    }

    record GuestPrincipal(UUID deviceId) implements PrimalPrincipal {
    }
}
