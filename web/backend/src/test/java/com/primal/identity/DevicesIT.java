package com.primal.identity;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.support.AuthHelper;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Устройства и аутентификация запросов")
class DevicesIT extends IntegrationTest {

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private ResultActions me(Cookie device) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/me").cookie(device));
    }

    private String deviceId(Cookie device) throws Exception {
        return JsonPath.read(me(device).andReturn().getResponse().getContentAsString(), "$.device.id");
    }

    /** Гостевое устройство, как после входа по ссылке-приглашению (этап 6). */
    private Cookie guest(String name) {
        String token = Tokens.newDeviceToken();
        jdbc.update("insert into device (id, token_hash, display_name) values (?, ?, ?)",
                UUID.randomUUID(), Tokens.sha256(token), name);
        return new Cookie(DeviceCookies.NAME, token);
    }

    @Nested
    @DisplayName("Доступ")
    class Access {

        @Test
        @DisplayName("каталог и проверка здоровья доступны без cookie")
        void publicEndpoints() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/catalog/bosses")).andExpect(status().isOk());
            mockMvc.perform(get("/api/actuator/health")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("защищённый запрос без cookie → 401 UNAUTHENTICATED в формате problem+json")
        void protectedWithoutCookie() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }

        @Test
        @DisplayName("неизвестный токен → 401")
        void unknownToken() throws Exception {
            // вызов и проверка
            me(new Cookie(DeviceCookies.NAME, Tokens.newDeviceToken())).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST без X-XSRF-TOKEN → 403, с токеном из cookie XSRF-TOKEN — проходит")
        void csrf() throws Exception {
            // подготовка
            Cookie device = auth.login("anna");
            MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                    .andExpect(status().isNoContent())
                    .andReturn();
            Cookie xsrf = csrfResult.getResponse().getCookie("XSRF-TOKEN");
            assertThat(xsrf).isNotNull();
            assertThat(xsrf.isHttpOnly()).isFalse();

            // вызов и проверка
            mockMvc.perform(post("/api/v1/auth/devices/revoke-others").cookie(device))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mockMvc.perform(post("/api/v1/auth/devices/revoke-others")
                            .cookie(device, xsrf)
                            .header("X-XSRF-TOKEN", xsrf.getValue()))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Кто я")
    class Me {

        @Test
        @DisplayName("пользователь: аккаунт и текущее устройство")
        void user() throws Exception {
            // подготовка
            Cookie device = auth.login("boris");

            // вызов и проверка
            me(device)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.kind").value("USER"))
                    .andExpect(jsonPath("$.user.login").value(AuthHelper.loginOf("boris")))
                    .andExpect(jsonPath("$.user.passwordSet").value(true))
                    .andExpect(jsonPath("$.user.displayName").value("boris"))
                    .andExpect(jsonPath("$.device.id").isString());
        }

        @Test
        @DisplayName("PATCH меняет имя пользователя; пустое имя сбрасывает его")
        void renameUser() throws Exception {
            // подготовка
            Cookie device = auth.login("vera");

            // вызов и проверка
            mockMvc.perform(patch("/api/v1/auth/me").with(xsrf()).cookie(device)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\": \"  Вера \"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.displayName").value("Вера"));
            mockMvc.perform(patch("/api/v1/auth/me").with(xsrf()).cookie(device)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\": \"\"}"))
                    .andExpect(jsonPath("$.user.displayName").isEmpty());
        }

        @Test
        @DisplayName("гость: kind GUEST, имя — у устройства; списка устройств нет")
        void guestMe() throws Exception {
            // подготовка
            Cookie device = guest("Вадим");

            // вызов и проверка
            me(device)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.kind").value("GUEST"))
                    .andExpect(jsonPath("$.user").isEmpty())
                    .andExpect(jsonPath("$.device.displayName").value("Вадим"));
            mockMvc.perform(patch("/api/v1/auth/me").with(xsrf()).cookie(device)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\": \"Вадим К.\"}"))
                    .andExpect(jsonPath("$.device.displayName").value("Вадим К."));
            mockMvc.perform(get("/api/v1/auth/devices").cookie(device))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_REQUIRED"));
        }
    }

    @Nested
    @DisplayName("Выход и отзыв")
    class Revocation {

        @Test
        @DisplayName("«Выйти» стирает cookie, устройство больше не действует")
        void logout() throws Exception {
            // подготовка
            Cookie device = auth.login("gleb");

            // вызов
            MvcResult result = mockMvc.perform(post("/api/v1/auth/logout").with(xsrf()).cookie(device))
                    .andExpect(status().isNoContent())
                    .andReturn();

            // проверка
            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("PRIMAL_DEVICE=;", "Max-Age=0");
            me(device).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("список устройств помечает текущее; отозванное устройство сразу получает 401")
        void revokeDevice() throws Exception {
            // подготовка: два браузера одного пользователя
            Cookie desktop = auth.login("dina");
            Cookie laptop = auth.login("dina");
            me(desktop).andExpect(status().isOk()); // устройство попало в кэш
            String desktopId = deviceId(desktop);

            // вызов
            mockMvc.perform(get("/api/v1/auth/devices").cookie(laptop))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[?(@.current == true)].id").value(deviceId(laptop)));
            mockMvc.perform(delete("/api/v1/auth/devices/{id}", desktopId).with(xsrf()).cookie(laptop))
                    .andExpect(status().isNoContent());

            // проверка
            me(desktop).andExpect(status().isUnauthorized());
            me(laptop).andExpect(status().isOk());
        }

        @Test
        @DisplayName("«Выйти на всех других устройствах» оставляет рабочим только текущее")
        void revokeOthers() throws Exception {
            // подготовка
            Cookie first = auth.login("egor");
            Cookie second = auth.login("egor");
            Cookie current = auth.login("egor");

            // вызов
            mockMvc.perform(post("/api/v1/auth/devices/revoke-others").with(xsrf()).cookie(current))
                    .andExpect(status().isNoContent());

            // проверка
            me(first).andExpect(status().isUnauthorized());
            me(second).andExpect(status().isUnauthorized());
            me(current).andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/auth/devices").cookie(current)).andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("чужое устройство не отзывается → 404")
        void foreignDevice() throws Exception {
            // подготовка
            Cookie mine = auth.login("zoya");
            Cookie foreign = auth.login("ilya");

            // вызов и проверка
            mockMvc.perform(delete("/api/v1/auth/devices/{id}", deviceId(foreign)).with(xsrf()).cookie(mine))
                    .andExpect(status().isNotFound());
            me(foreign).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Срок жизни устройства")
    class Lifetime {

        @Test
        @DisplayName("через 30 дней cookie продлевается тем же токеном, дальше — нет")
        void cookieRenewal() throws Exception {
            // подготовка
            Cookie device = auth.login("klim");
            clock.advance(Duration.ofDays(29));
            me(device).andExpect(cookie().doesNotExist(DeviceCookies.NAME));

            // вызов
            clock.advance(Duration.ofDays(2));
            MvcResult renewed = me(device).andExpect(status().isOk()).andReturn();

            // проверка
            Cookie cookie = renewed.getResponse().getCookie(DeviceCookies.NAME);
            assertThat(cookie).isNotNull();
            assertThat(cookie.getValue()).isEqualTo(device.getValue());
            assertThat(cookie.getMaxAge()).isEqualTo((int) Duration.ofDays(400).toSeconds());
            me(device).andExpect(cookie().doesNotExist(DeviceCookies.NAME));
        }

        @Test
        @DisplayName("устройство без визитов больше года отключается")
        void idleDeviceIsDisabled() throws Exception {
            // подготовка
            Cookie device = auth.login("lida");
            me(device).andExpect(status().isOk());

            // вызов
            clock.advance(Duration.ofDays(366));

            // проверка
            me(device).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("время визита пишется в БД не чаще раза в час")
        void lastSeenIsThrottled() throws Exception {
            // подготовка
            Cookie device = auth.login("mark");
            Instant start = jdbc.queryForObject("select last_seen_at from device", Timestamp.class).toInstant();

            // вызов и проверка
            clock.set(start.plus(Duration.ofMinutes(30)));
            me(device).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("select last_seen_at from device", Timestamp.class).toInstant()).isEqualTo(start);
            clock.set(start.plus(Duration.ofMinutes(61)));
            me(device).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("select last_seen_at from device", Timestamp.class).toInstant())
                    .isEqualTo(start.plus(Duration.ofMinutes(61)));
        }
    }
}
