package com.primal.identity;

import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.DeviceService.DeviceSummary;
import com.primal.identity.PrimalPrincipal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** «Мои устройства» ({@code doc/api.md} §3) — только у пользователя с аккаунтом. */
@Tag(name = "auth", description = "Вход по логину и паролю и устройства")
@RestController
@RequestMapping("/api/v1/auth/devices")
class DevicesController {

    private final DeviceService devices;
    private final DeviceCookies cookies;

    DevicesController(DeviceService devices, DeviceCookies cookies) {
        this.devices = devices;
        this.cookies = cookies;
    }

    @Operation(operationId = "listDevices")
    @GetMapping
    List<DeviceSummary> list(@AuthenticationPrincipal PrimalPrincipal principal) {
        return devices.list(user(principal));
    }

    /** Отзыв устройства; отзыв текущего — то же, что «Выйти». */
    @Operation(operationId = "revokeDevice")
    @ApiResponse(responseCode = "204", description = "Устройство отозвано")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> revoke(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable UUID id) {
        devices.revoke(user(principal), id);
        ResponseEntity.HeadersBuilder<?> response = ResponseEntity.noContent();
        if (id.equals(principal.deviceId())) {
            response.header(HttpHeaders.SET_COOKIE, cookies.clear().toString());
        }
        return response.build();
    }

    /** «Выйти на всех других устройствах». */
    @Operation(operationId = "revokeOtherDevices")
    @ApiResponse(responseCode = "204", description = "Другие устройства отозваны")
    @PostMapping("/revoke-others")
    ResponseEntity<Void> revokeOthers(@AuthenticationPrincipal PrimalPrincipal principal) {
        devices.revokeOthers(user(principal));
        return ResponseEntity.noContent().build();
    }

    private static UserPrincipal user(PrimalPrincipal principal) {
        if (principal instanceof UserPrincipal user) {
            return user;
        }
        throw new ApiException(ErrorCode.ACCOUNT_REQUIRED, "Список устройств есть только у аккаунта. Войдите по логину и паролю.");
    }
}
