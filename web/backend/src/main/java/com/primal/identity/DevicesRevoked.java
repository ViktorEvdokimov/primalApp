package com.primal.identity;

import java.util.List;
import java.util.UUID;

/** Устройства отозваны («Выйти», отзыв из списка, вход заново в этом браузере): их потоки событий закрываются. */
public record DevicesRevoked(List<UUID> deviceIds) {

    public DevicesRevoked {
        deviceIds = List.copyOf(deviceIds);
    }
}
