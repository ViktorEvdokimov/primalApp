package com.primal.identity;

import com.primal.common.api.ApiNullable;
import com.primal.identity.PasswordAuthService.Registration;
import com.primal.identity.PasswordAuthService.SignIn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.InetAddress;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Регистрация и вход по номеру телефона и паролю, «кто я» ({@code doc/api.md} §3). */
@Tag(name = "auth", description = "Регистрация, вход по номеру телефона и паролю, устройства")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private static final String PASSWORD_RULE = "Пароль — от 8 до 64 символов";

    /** Номер — логин, формат приводится к {@code +79123456789}; имя обязательно: его видят участники кампаний. */
    record RegisterRequest(
            @NotBlank(message = "Укажите номер телефона") @Size(max = 24, message = "Слишком длинный номер")
            String phone,
            @NotBlank(message = "Укажите пароль")
            @Size(min = Credentials.PASSWORD_MIN, max = Credentials.PASSWORD_MAX, message = PASSWORD_RULE)
            String password,
            @NotBlank(message = "Укажите имя") @Size(max = 60, message = "Не длиннее 60 символов") String displayName) {

        @Override
        public String toString() {
            return "RegisterRequest[phone=" + phone + "]";
        }
    }

    record LoginRequest(
            @NotBlank(message = "Укажите номер телефона") @Size(max = 24, message = "Слишком длинный номер")
            String phone,
            @NotBlank(message = "Укажите пароль") @Size(max = 128, message = "Слишком длинный пароль") String password) {

        @Override
        public String toString() {
            return "LoginRequest[phone=" + phone + "]";
        }
    }

    /**
     * {@code phone} — логин; {@code null} и {@code passwordSet = false} бывают у аккаунта, созданного по почте:
     * он задаёт номер и пароль в настройках (пароль — без текущего).
     */
    record UserView(long id, @ApiNullable String phone, @ApiNullable String displayName, boolean passwordSet) {
    }

    record DeviceView(UUID id, String userAgent) {
    }

    record SignInResponse(UserView user, DeviceView device) {
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

    /** Новый номер — новый логин; удалить номер нельзя. */
    record PhoneRequest(
            @NotBlank(message = "Укажите номер телефона") @Size(max = 24, message = "Слишком длинный номер")
            String phone) {
    }

    /** {@code currentPassword} не нужен, если пароля ещё нет ({@code passwordSet = false}). */
    record PasswordChangeRequest(
            @ApiNullable @Size(max = 128, message = "Слишком длинный пароль") String currentPassword,
            @NotBlank(message = "Укажите новый пароль")
            @Size(min = Credentials.PASSWORD_MIN, max = Credentials.PASSWORD_MAX, message = PASSWORD_RULE)
            String newPassword) {

        @Override
        public String toString() {
            return "PasswordChangeRequest[]";
        }
    }

    private final PasswordAuthService auth;
    private final AccountService accounts;
    private final DeviceService devices;
    private final DeviceCookies cookies;

    AuthController(PasswordAuthService auth, AccountService accounts, DeviceService devices, DeviceCookies cookies) {
        this.auth = auth;
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

    @Operation(operationId = "register")
    @ApiResponse(responseCode = "201", description = "Аккаунт создан, браузер запомнен")
    @PostMapping("/register")
    ResponseEntity<SignInResponse> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        SignIn signIn = auth.register(new Registration(body.phone(), body.password(), body.displayName()),
                clientIp(request), cookies.read(request), request.getHeader(HttpHeaders.USER_AGENT));
        return signedIn(HttpStatus.CREATED, signIn);
    }

    @Operation(operationId = "login")
    @PostMapping("/login")
    ResponseEntity<SignInResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        SignIn signIn = auth.login(body.phone(), body.password(), clientIp(request), cookies.read(request),
                request.getHeader(HttpHeaders.USER_AGENT));
        return signedIn(HttpStatus.OK, signIn);
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

    @Operation(operationId = "updatePhone")
    @PutMapping("/me/phone")
    MeResponse changePhone(@AuthenticationPrincipal PrimalPrincipal principal, @Valid @RequestBody PhoneRequest body) {
        return toResponse(accounts.changePhone(principal, body.phone()));
    }

    @Operation(operationId = "changePassword")
    @ApiResponse(responseCode = "204", description = "Пароль изменён")
    @PutMapping("/me/password")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal PrimalPrincipal principal,
                                        @Valid @RequestBody PasswordChangeRequest body) {
        accounts.changePassword(principal, body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** Выход на этом устройстве: устройство отзывается, cookie стирается. */
    @Operation(operationId = "logout")
    @ApiResponse(responseCode = "204", description = "Устройство отозвано, cookie стёрта")
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal PrimalPrincipal principal) {
        devices.logout(principal);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    private ResponseEntity<SignInResponse> signedIn(HttpStatus status, SignIn signIn) {
        SignInResponse response = new SignInResponse(userView(signIn.user()),
                new DeviceView(signIn.device().id(), signIn.device().userAgent()));
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.issue(signIn.device().token()).toString())
                .body(response);
    }

    private static UserView userView(AppUser user) {
        return new UserView(user.getId(), user.getPhone(), user.getDisplayName(), user.getPasswordHash() != null);
    }

    private static MeResponse toResponse(AccountService.Me me) {
        UserView user = me.user().map(AuthController::userView).orElse(null);
        return new MeResponse(user == null ? Kind.GUEST : Kind.USER, user,
                new MeDevice(me.principal().deviceId(), me.deviceName()));
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
