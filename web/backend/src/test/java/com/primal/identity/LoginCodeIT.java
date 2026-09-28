package com.primal.identity;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.common.config.PrimalProperties;
import com.primal.support.IntegrationTest;
import com.primal.support.MailCapture;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Вход по коду из письма")
class LoginCodeIT extends IntegrationTest {

    private static final String ANDROID_CHROME =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36";

    @Autowired
    private LoginCodeService loginCodes;

    @Autowired
    private PrimalProperties properties;

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private ResultActions requestCode(String email, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/code")
                .with(xsrf())
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\"}"));
    }

    private ResultActions requestCode(String email) throws Exception {
        return requestCode(email, "127.0.0.1");
    }

    private String challengeId(ResultActions actions) throws Exception {
        return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.challengeId");
    }

    private ResultActions verify(String challengeId, String code, Cookie... cookies) throws Exception {
        var request = post("/api/v1/auth/code/verify")
                .with(xsrf())
                .header(HttpHeaders.USER_AGENT, ANDROID_CHROME)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\": \"" + challengeId + "\", \"code\": \"" + code + "\"}");
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request);
    }

    /** Запрос кода и сам код из письма. */
    private Map.Entry<String, String> codeFor(String email) throws Exception {
        int alreadySent = mail.sentTo(email).size();
        String challengeId = challengeId(requestCode(email).andExpect(status().isAccepted()));
        return Map.entry(challengeId, mail.awaitCode(email, alreadySent));
    }

    /** Код, который точно не совпадает с настоящим. */
    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Nested
    @DisplayName("Письмо с кодом")
    class Letter {

        @Test
        @DisplayName("уходит на нормализованный адрес; код действует 10 минут, повторить можно через минуту")
        void codeIsSent() throws Exception {
            // подготовка
            Instant now = Instant.parse("2026-09-27T18:04:11Z");
            clock.set(now);

            // вызов
            requestCode("  Alice@Example.COM ")
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.challengeId").isString())
                    .andExpect(jsonPath("$.expiresAt").value("2026-09-27T18:14:11Z"))
                    .andExpect(jsonPath("$.resendAfter").value("2026-09-27T18:05:11Z"));

            // проверка
            MimeMessage letter = mail.awaitMessageTo("alice@example.com", 0);
            String code = mail.awaitCode("alice@example.com", 0);
            assertThat(letter.getSubject()).isEqualTo("Код входа в Primal: " + code);
            assertThat(letter.getFrom()[0].toString()).contains("no-reply@primal.local");
            assertThat(MailCapture.text(letter))
                    .contains("Код входа в Primal: " + code)
                    .contains("Действует 10 минут. Если вы не запрашивали код, проигнорируйте письмо.");
            assertThat(MailCapture.html(letter)).contains(code);
        }

        @Test
        @DisplayName("ответ одинаков для нового и известного адреса")
        void sameAnswerForKnownAndNewEmail() throws Exception {
            // подготовка
            auth.login("known@example.com");
            rateLimiter.reset();

            // вызов
            MvcResult known = requestCode("known@example.com").andReturn();
            MvcResult unknown = requestCode("new@example.com").andReturn();

            // проверка
            assertThat(known.getResponse().getStatus()).isEqualTo(unknown.getResponse().getStatus()).isEqualTo(202);
            Map<String, Object> knownBody = JsonPath.read(known.getResponse().getContentAsString(), "$");
            Map<String, Object> unknownBody = JsonPath.read(unknown.getResponse().getContentAsString(), "$");
            assertThat(knownBody.keySet()).isEqualTo(unknownBody.keySet());
            mail.awaitMessageTo("new@example.com", 0);
        }

        @Test
        @DisplayName("SMTP недоступен — ответ тот же, код можно запросить снова")
        void smtpFailureIsNotShownToUser() throws Exception {
            // подготовка
            mail.failWith(new MailSendException("SMTP недоступен"));

            // вызов и проверка
            requestCode("bob@example.com").andExpect(status().isAccepted());
            Thread.sleep(200);
            assertThat(mail.sentTo("bob@example.com")).isEmpty();
        }

        @Test
        @DisplayName("некорректная почта → 400 VALIDATION_FAILED")
        void invalidEmail() throws Exception {
            // вызов и проверка
            requestCode("not-an-email")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("email"));
        }
    }

    @Nested
    @DisplayName("Ограничения частоты")
    class RateLimits {

        @Test
        @DisplayName("второе письмо на адрес в течение минуты → 429 RATE_LIMITED с Retry-After")
        void onePerMinutePerEmail() throws Exception {
            // подготовка
            requestCode("carol@example.com").andExpect(status().isAccepted());

            // вызов и проверка
            MvcResult result = requestCode("carol@example.com")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                    .andReturn();
            int retryAfter = Integer.parseInt(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
            assertThat(retryAfter).isBetween(1, 60);
        }

        @Test
        @DisplayName("не больше 20 писем в час с одного IP")
        void twentyPerHourPerIp() throws Exception {
            // подготовка
            for (int i = 0; i < 20; i++) {
                requestCode("user" + i + "@example.com", "10.0.0.7").andExpect(status().isAccepted());
            }

            // вызов и проверка
            requestCode("user20@example.com", "10.0.0.7").andExpect(status().isTooManyRequests());
            requestCode("user20@example.com", "10.0.0.8").andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("не больше 60 проверок кода в час с одного IP")
        void sixtyVerificationsPerHourPerIp() throws Exception {
            // подготовка
            for (int i = 0; i < 60; i++) {
                verify(UUID.randomUUID().toString(), "123456").andExpect(status().isBadRequest());
            }

            // вызов и проверка
            verify(UUID.randomUUID().toString(), "123456")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        }
    }

    @Nested
    @DisplayName("Проверка кода")
    class Verification {

        @Test
        @DisplayName("верный код создаёт пользователя и устройство, выдаёт cookie устройства")
        void firstSignIn() throws Exception {
            // подготовка
            Map.Entry<String, String> code = codeFor("dave@example.com");

            // вызов
            MvcResult result = verify(code.getKey(), code.getValue())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isNewUser").value(true))
                    .andExpect(jsonPath("$.user.email").value("dave@example.com"))
                    .andExpect(jsonPath("$.user.displayName").isEmpty())
                    .andExpect(jsonPath("$.device.userAgent").value("Chrome, Android"))
                    .andReturn();

            // проверка
            String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie)
                    .startsWith("PRIMAL_DEVICE=")
                    .contains("Path=/api", "Max-Age=34560000", "HttpOnly", "SameSite=Lax")
                    .doesNotContain("Secure");
            String token = result.getResponse().getCookie(DeviceCookies.NAME).getValue();
            assertThat(count("app_user")).isEqualTo(1);
            byte[] tokenHash = jdbc.queryForObject("select token_hash from device", byte[].class);
            assertThat(tokenHash).isEqualTo(Tokens.sha256(token));
        }

        @Test
        @DisplayName("повторный вход тем же адресом не создаёт пользователя")
        void secondSignIn() throws Exception {
            // подготовка
            auth.login("erin@example.com");
            rateLimiter.reset();
            Map.Entry<String, String> code = codeFor("ERIN@example.com");

            // вызов и проверка
            verify(code.getKey(), code.getValue())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isNewUser").value(false));
            assertThat(count("app_user")).isEqualTo(1);
            assertThat(count("device")).isEqualTo(2);
        }

        @Test
        @DisplayName("неверный код → 400 INVALID_CODE, попыток становится меньше")
        void wrongCode() throws Exception {
            // подготовка
            Map.Entry<String, String> code = codeFor("frank@example.com");

            // вызов и проверка
            verify(code.getKey(), wrong(code.getValue()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CODE"))
                    .andExpect(jsonPath("$.attemptsLeft").value(4));
            assertThat(count("app_user")).isZero();
        }

        @Test
        @DisplayName("после 5 неверных попыток 6-я → CODE_EXPIRED, даже с верным кодом")
        void attemptsExhausted() throws Exception {
            // подготовка
            Map.Entry<String, String> code = codeFor("grace@example.com");
            for (int attemptsLeft = 4; attemptsLeft >= 0; attemptsLeft--) {
                verify(code.getKey(), wrong(code.getValue())).andExpect(jsonPath("$.attemptsLeft").value(attemptsLeft));
            }

            // вызов и проверка
            verify(code.getKey(), code.getValue())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        }

        @Test
        @DisplayName("код старше 10 минут → CODE_EXPIRED")
        void codeExpires() throws Exception {
            // подготовка
            Map.Entry<String, String> code = codeFor("heidi@example.com");

            // вызов
            clock.advance(Duration.ofMinutes(10));

            // проверка
            verify(code.getKey(), code.getValue()).andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        }

        @Test
        @DisplayName("код действует один раз")
        void codeIsSingleUse() throws Exception {
            // подготовка
            Map.Entry<String, String> code = codeFor("ivan@example.com");
            verify(code.getKey(), code.getValue()).andExpect(status().isOk());

            // вызов и проверка
            verify(code.getKey(), code.getValue()).andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        }

        @Test
        @DisplayName("новый запрос кода отменяет прежний")
        void newCodeReplacesOld() throws Exception {
            // подготовка
            Map.Entry<String, String> first = codeFor("judy@example.com");
            rateLimiter.reset();
            Map.Entry<String, String> second = codeFor("judy@example.com");

            // вызов и проверка
            verify(first.getKey(), first.getValue()).andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
            verify(second.getKey(), second.getValue()).andExpect(status().isOk());
        }

        @Test
        @DisplayName("гостевое устройство браузера привязывается к аккаунту, новое не создаётся")
        void guestDeviceIsAttached() throws Exception {
            // подготовка: браузер уже открывал ссылку-приглашение
            UUID guestId = UUID.randomUUID();
            String guestToken = Tokens.newDeviceToken();
            jdbc.update("insert into device (id, token_hash, display_name) values (?, ?, 'Вадим')",
                    guestId, Tokens.sha256(guestToken));
            Map.Entry<String, String> code = codeFor("ken@example.com");

            // вызов
            verify(code.getKey(), code.getValue(), new Cookie(DeviceCookies.NAME, guestToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.device.id").value(guestId.toString()));

            // проверка
            assertThat(count("device")).isEqualTo(1);
            Long userId = jdbc.queryForObject("select user_id from device where id = ?", Long.class, guestId);
            Long accountId = jdbc.queryForObject("select id from app_user where email = 'ken@example.com'", Long.class);
            assertThat(userId).isEqualTo(accountId);
        }

        @Test
        @DisplayName("вход другим аккаунтом в том же браузере отзывает прежнее устройство")
        void previousDeviceIsRevoked() throws Exception {
            // подготовка
            Cookie previous = auth.login("leo@example.com");
            rateLimiter.reset();
            Map.Entry<String, String> code = codeFor("mia@example.com");

            // вызов
            verify(code.getKey(), code.getValue(), previous).andExpect(status().isOk());

            // проверка
            Integer revoked = jdbc.queryForObject(
                    "select count(*) from device d join app_user u on u.id = d.user_id "
                            + "where u.email = 'leo@example.com' and d.revoked_at is not null", Integer.class);
            assertThat(revoked).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Хранение")
    class Storage {

        @Test
        @DisplayName("в БД только HMAC кода и SHA-256 токена устройства, открытых значений нет")
        void onlyHashesAreStored() throws Exception {
            // подготовка
            Map.Entry<String, String> code = codeFor("nina@example.com");
            MvcResult result = verify(code.getKey(), code.getValue()).andReturn();
            String token = result.getResponse().getCookie(DeviceCookies.NAME).getValue();

            // вызов
            byte[] codeHash = jdbc.queryForObject("select code_hash from login_challenge", byte[].class);
            byte[] tokenHash = jdbc.queryForObject("select token_hash from device", byte[].class);

            // проверка
            UUID challengeId = UUID.fromString(code.getKey());
            assertThat(codeHash)
                    .hasSize(32)
                    .isEqualTo(Tokens.codeHash(properties.otp().pepper(), challengeId, code.getValue()))
                    .isNotEqualTo(code.getValue().getBytes(StandardCharsets.US_ASCII));
            assertThat(tokenHash).hasSize(32).isEqualTo(Tokens.sha256(token));
            assertThat(new String(tokenHash, StandardCharsets.ISO_8859_1)).doesNotContain(token);
        }

        @Test
        @DisplayName("запросы кодов старше суток удаляются фоновой задачей")
        void staleChallengesAreDeleted() throws Exception {
            // подготовка
            clock.set(Instant.now().minus(Duration.ofDays(2)));
            requestCode("old@example.com").andExpect(status().isAccepted());
            clock.reset();
            requestCode("fresh@example.com").andExpect(status().isAccepted());

            // вызов
            int deleted = loginCodes.deleteStaleChallenges();

            // проверка
            assertThat(deleted).isEqualTo(1);
            assertThat(jdbc.queryForList("select email from login_challenge", String.class))
                    .containsExactly("fresh@example.com");
        }
    }
}
