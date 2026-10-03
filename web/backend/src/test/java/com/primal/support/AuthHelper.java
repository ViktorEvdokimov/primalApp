package com.primal.support;

import static com.primal.support.Xsrf.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.primal.common.ratelimit.RateLimiter;
import com.primal.identity.DeviceCookies;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Вход в тестах тем же путём, что у пользователя: вход по логину и паролю, а если аккаунта ещё нет —
 * регистрация. Тест называет пользователя именем («alice»): оно становится именем аккаунта, а логин
 * выдаётся один на имя ({@link #loginOf}). Пароль у всех — {@link #PASSWORD}.
 */
@TestComponent
public class AuthHelper {

    public static final String PASSWORD = "test-password";

    private static final Map<String, String> LOGINS = new ConcurrentHashMap<>();

    private final MockMvc mockMvc;
    private final RateLimiter rateLimiter;

    public AuthHelper(MockMvc mockMvc, RateLimiter rateLimiter) {
        this.mockMvc = mockMvc;
        this.rateLimiter = rateLimiter;
    }

    /** Логин пользователя с этим именем: {@code user-alice}, {@code user-bob}… — один на весь прогон. */
    public static String loginOf(String name) {
        return LOGINS.computeIfAbsent(name, ignored -> "user-" + name);
    }

    /** Вход пользователя; возвращает cookie устройства. Ограничения частоты сбрасываются. */
    public Cookie login(String name) throws Exception {
        return login(name, null);
    }

    /** Вход в браузере, где уже есть cookie устройства (например, гостя по ссылке-приглашению). */
    public Cookie login(String name, Cookie current) throws Exception {
        rateLimiter.reset();
        String login = loginOf(name);
        MvcResult result = perform("/api/v1/auth/login",
                "{\"login\": \"" + login + "\", \"password\": \"" + PASSWORD + "\"}", current);
        if (result.getResponse().getStatus() != 200) {
            result = perform("/api/v1/auth/register", "{\"login\": \"" + login + "\", \"password\": \"" + PASSWORD
                    + "\", \"displayName\": \"" + name + "\"}", current);
        }
        int status = result.getResponse().getStatus();
        if (status != 200 && status != 201) {
            throw new AssertionError("Вход «" + name + "» не удался: " + status + " "
                    + result.getResponse().getContentAsString());
        }
        return result.getResponse().getCookie(DeviceCookies.NAME);
    }

    private MvcResult perform(String url, String body, Cookie current) throws Exception {
        MockHttpServletRequestBuilder request = post(url)
                .with(xsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (current != null) {
            request.cookie(current);
        }
        return mockMvc.perform(request).andReturn();
    }
}
