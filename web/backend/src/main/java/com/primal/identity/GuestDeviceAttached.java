package com.primal.identity;

import java.util.UUID;

/**
 * Гостевое устройство (открывали ссылку-приглашение) вошло в аккаунт: его доступы по ссылкам переходят
 * пользователю ({@code doc/data-model.md} §3.5). Публикуется в транзакции входа.
 */
public record GuestDeviceAttached(UUID deviceId, long userId) {
}
