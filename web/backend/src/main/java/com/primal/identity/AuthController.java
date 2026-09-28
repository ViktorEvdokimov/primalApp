package com.primal.identity;

import com.primal.common.api.ApiNullable;
import com.primal.identity.LoginCodeService.CodeRequested;
import com.primal.identity.LoginCodeService.SignIn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Вход по коду из письма ({@code doc/api.md} §3). */
@Tag(name = "auth", description = "Вход по коду из письма и устройства")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    /** Почта обрезается по краям до проверки формата. */
    record CodeRequest(
            @NotBlank(message = "Укажите почту")
            @Email(message = "Некорректный адрес почты")
            @Size(max = 254, message = "Слишком длинный адрес")
            String email) {

        CodeRequest {
            email = email == null ? null : email.trim();
        }
    }

    record CodeResponse(UUID challengeId, Instant expiresAt, Instant resendAfter) {
    }

    record VerifyRequest(
            @NotNull(message = "Нет запроса кода") UUID challengeId,
            @NotBlank(message = "Введите код") @Pattern(regexp = "\\d{6}", message = "Код — 6 цифр") String code) {
    }

    record UserView(long id, String email, @ApiNullable String displayName) {
    }

    record DeviceView(UUID id, String userAgent) {
    }

    record SignInResponse(UserView user, boolean isNewUser, DeviceView device) {
    }

    enum Kind { USER, GUEST }

    /** Устройство в «Кто я»: у гостя — его имя, у пользователя имени устройства нет. */
    record MeDevice(UUID id, @ApiNullable String displayName) {
    }

    /** {@code user} — только у пользователя с аккаунтом. */
    record MeResponse(Kind kind, @ApiNullable UserView user, MeDevice device) {
    }

    record RenameRequest(@Size(max = 60, message = "Не длиннее 60 символов") String displayName) {
    }

    private final LoginCodeService loginCodes;
    private final AccountService accounts;
    private final DeviceService devices;
    private final DeviceCookies cookies;

    AuthController(LoginCodeService loginCodes, AccountService accounts, DeviceService devices, DeviceCookies cookies) {
        this.loginCodes = loginCodes;
        this.accounts = accounts;
        this.devices = devices;
        this.cookies = cookies;
    }

    /**
     * Выдаёт cookie {@code XSRF-TOKEN}, если её ещё нет: фронтенд вызывает перед первым изменяющим запросом.
     * Чтение токена заставляет Spring Security записать cookie.
     */
    @Operation(operationId = "getCsrfToken")
    @ApiResponse(responseCode = "204", description = "Cookie XSRF-TOKEN выдана")
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(@Parameter(hidden = true) CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "getMe")
    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal PrimalPrincipal principal) {
        return toResponse(accounts.me(principal));
    }

    @Operation(operationId = "updateMe")
    @PatchMapping("/me")
    MeResponse rename(@AuthenticationPrincipal PrimalPrincipal principal, @Valid @RequestBody RenameRequest body) {
        return toResponse(accounts.rename(principal, body.displayName()));
    }

    /** Выход на этом устройстве: устройство отзывается, cookie стирается. */
    @Operation(operationId = "logout")
    @ApiResponse(responseCode = "204", description = "Устройство отозвано, cookie стёрта")
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal PrimalPrincipal principal) {
        devices.logout(principal);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    private static MeResponse toResponse(AccountService.Me me) {
        UserView user = me.user()
                .map(account -> new UserView(account.getId(), account.getEmail(), account.getDisplayName()))
                .orElse(null);
        return new MeResponse(user == null ? Kind.GUEST : Kind.USER, user,
                new MeDevice(me.principal().deviceId(), me.deviceName()));
    }

    @Operation(operationId = "requestCode")
    @ApiResponse(responseCode = "202", description = "Код отправлен")
    @PostMapping("/code")
    ResponseEntity<CodeResponse> requestCode(@Valid @RequestBody CodeRequest body, HttpServletRequest request) {
        CodeRequested requested = loginCodes.requestCode(body.email(), clientIp(request));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new CodeResponse(requested.challengeId(), requested.expiresAt(), requested.resendAfter()));
    }

    @Operation(operationId = "verifyCode")
    @PostMapping("/code/verify")
    ResponseEntity<SignInResponse> verify(@Valid @RequestBody VerifyRequest body, HttpServletRequest request) {
        SignIn signIn = loginCodes.verify(body.challengeId(), body.code(), clientIp(request), cookies.read(request),
                request.getHeader(HttpHeaders.USER_AGENT));
        AppUser user = signIn.user();
        SignInResponse response = new SignInResponse(
                new UserView(user.getId(), user.getEmail(), user.getDisplayName()),
                signIn.newUser(),
                new DeviceView(signIn.device().id(), signIn.device().userAgent()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(signIn.device().token()).toString())
                .body(response);
    }

    /** Адрес клиента; за Caddy его подставляет {@code X-Forwarded-For} ({@code server.forward-headers-strategy}). */
    private static InetAddress clientIp(HttpServletRequest request) {
        try {
            return InetAddress.ofLiteral(request.getRemoteAddr());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
