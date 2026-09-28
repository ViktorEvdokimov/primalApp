package com.primal.support;

import static com.primal.support.Xsrf.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.common.ratelimit.RateLimiter;
import com.primal.identity.DeviceCookies;
import jakarta.servlet.http.Cookie;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Вход в тестах тем же путём, что у пользователя: запрос кода, код из письма, проверка кода. */
@TestComponent
public class AuthHelper {

    private final MockMvc mockMvc;
    private final MailCapture mail;
    private final RateLimiter rateLimiter;

    public AuthHelper(MockMvc mockMvc, MailCapture mail, RateLimiter rateLimiter) {
        this.mockMvc = mockMvc;
        this.mail = mail;
        this.rateLimiter = rateLimiter;
    }

    /** Запрашивает код; возвращает {@code challengeId}. */
    public String requestCode(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/code")
                        .with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.challengeId");
    }

    /** Вход пользователя; возвращает cookie устройства. Ограничения частоты сбрасываются. */
    public Cookie login(String email) throws Exception {
        return login(email, null);
    }

    /** Вход в браузере, где уже есть cookie устройства (например, гостя по ссылке-приглашению). */
    public Cookie login(String email, Cookie current) throws Exception {
        rateLimiter.reset();
        int alreadySent = mail.sentTo(email).size();
        String challengeId = requestCode(email);
        String code = mail.awaitCode(email, alreadySent);
        MockHttpServletRequestBuilder verify = post("/api/v1/auth/code/verify")
                .with(xsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\": \"" + challengeId + "\", \"code\": \"" + code + "\"}");
        if (current != null) {
            verify.cookie(current);
        }
        MvcResult result = mockMvc.perform(verify).andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie(DeviceCookies.NAME);
    }
}
