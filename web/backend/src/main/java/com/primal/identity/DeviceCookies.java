package com.primal.identity;

import com.primal.common.config.PrimalProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * Cookie устройства {@code PRIMAL_DEVICE}: HttpOnly, SameSite=Lax, только для {@code /api}; {@code Secure},
 * если сайт открыт по HTTPS. Срок — 400 дней, дольше браузеры не хранят.
 */
@Component
public class DeviceCookies {

    public static final String NAME = "PRIMAL_DEVICE";
    static final Duration MAX_AGE = Duration.ofDays(400);
    private static final String PATH = "/api";

    private final boolean secure;

    public DeviceCookies(PrimalProperties properties) {
        this.secure = properties.isHttps();
    }

    public ResponseCookie issue(String token) {
        return base(token).maxAge(MAX_AGE).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public Optional<String> read(HttpServletRequest request) {
        return Optional.ofNullable(WebUtils.getCookie(request, NAME))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank());
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite("Lax").path(PATH);
    }
}
