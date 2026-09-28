package com.primal.support;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * CSRF в тестах так же, как в браузере (double-submit): cookie {@code XSRF-TOKEN} и тот же токен в заголовке
 * {@code X-XSRF-TOKEN}. Помощник spring-security-test {@code csrf()} с режимом {@code csrf.spa()} не согласуется.
 */
public final class Xsrf {

    private Xsrf() {
    }

    public static RequestPostProcessor xsrf() {
        return request -> {
            String token = UUID.randomUUID().toString();
            List<Cookie> cookies = new ArrayList<>(request.getCookies() == null ? List.of() : Arrays.asList(request.getCookies()));
            cookies.add(new Cookie("XSRF-TOKEN", token));
            request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }
}
