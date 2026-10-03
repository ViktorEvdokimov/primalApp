package com.primal.identity;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.primal.support.AuthHelper;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Регистрация и вход по логину и паролю")
class PasswordAuthIT extends IntegrationTest {

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private ResultActions register(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions register(String login, String password) throws Exception {
        return register("{\"login\": \"" + login + "\", \"password\": \"" + password + "\", \"displayName\": \"Игрок\"}");
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\": \"" + login + "\", \"password\": \"" + password + "\"}"));
    }

    private ResultActions me(Cookie device) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/me").cookie(device));
    }

    private ResultActions changeLogin(Cookie device, String login) throws Exception {
        return mockMvc.perform(put("/api/v1/auth/me/login").with(xsrf()).cookie(device)
                .contentType(MediaType.APPLICATION_JSON).content("{\"login\": \"" + login + "\"}"));
    }

    private static Cookie device(ResultActions result) {
        return result.andReturn().getResponse().getCookie(DeviceCookies.NAME);
    }

    @Nested
    @DisplayName("Регистрация")
    class Register {

        @Test
        @DisplayName("логин приводится к нижнему регистру, имя сохраняется, пароль — только хешем, браузер запоминается")
        void createsAccountAndDevice() throws Exception {
            // вызов
            ResultActions result = register("""
                    {"login": "Alice_01", "password": "correct horse", "displayName": " Алиса "}""")
                    .andExpect(status().isCreated())
                    .andExpect(cookie().httpOnly(DeviceCookies.NAME, true))
                    .andExpect(jsonPath("$.user.login").value("alice_01"))
                    .andExpect(jsonPath("$.user.displayName").value("Алиса"))
                    .andExpect(jsonPath("$.user.passwordSet").value(true))
                    .andExpect(jsonPath("$.device.id").isString());

            // проверка
            String hash = jdbc.queryForObject("select password_hash from app_user where login = 'alice_01'", String.class);
            assertThat(hash).startsWith("{bcrypt}$2").doesNotContain("correct horse");
            me(device(result)).andExpect(jsonPath("$.kind").value("USER"))
                    .andExpect(jsonPath("$.user.login").value("alice_01"));
        }

        @Test
        @DisplayName("логин уже зарегистрирован — 409 LOGIN_TAKEN, в любом регистре")
        void loginTaken() throws Exception {
            // подготовка
            register("Alice", "12345678").andExpect(status().isCreated());

            // вызов и проверка
            register("ALICE", "87654321")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LOGIN_TAKEN"))
                    .andExpect(jsonPath("$.detail").value("Логин alice уже зарегистрирован. Войдите по нему."));
        }

        @Test
        @DisplayName("логин, пароль и имя обязательны и проверяются: ошибки по полям")
        void validation() throws Exception {
            // вызов и проверка: всё пусто
            register("{\"login\": \"\", \"password\": \"\", \"displayName\": \" \"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[?(@.field == 'login')]").exists())
                    .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
                    .andExpect(jsonPath("$.errors[?(@.field == 'displayName')].message").value("Укажите имя"));
            // короткий пароль
            register("alice", "short")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].message").value("Пароль — от 8 до 64 символов"));
            // недопустимый символ в логине
            register("ab!", "12345678")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("login"));
            // слишком короткий логин
            register("ab", "12345678")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("login"));
            // 40 кириллических букв — 80 байт: bcrypt столько не учитывает
            register("alice", "я".repeat(40))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("password"));

            // проверка
            assertThat(jdbc.queryForObject("select count(*) from app_user", Integer.class)).isZero();
        }

        @Test
        @DisplayName("гость по ссылке регистрируется — его устройство становится устройством аккаунта")
        void guestDeviceIsKept() throws Exception {
            // подготовка
            String token = Tokens.newDeviceToken();
            UUID guestId = UUID.randomUUID();
            jdbc.update("insert into device (id, token_hash, display_name) values (?, ?, 'Вадим')",
                    guestId, Tokens.sha256(token));

            // вызов
            mockMvc.perform(post("/api/v1/auth/register").with(xsrf()).cookie(new Cookie(DeviceCookies.NAME, token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"login\": \"vadim-guest\", \"password\": \"12345678\", \"displayName\": \"Вадим\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.device.id").value(guestId.toString()));

            // проверка
            Long owner = jdbc.queryForObject("select user_id from device where id = ?", Long.class, guestId);
            assertThat(owner).isEqualTo(jdbc.queryForObject("select id from app_user where login = 'vadim-guest'", Long.class));
        }
    }

    @Nested
    @DisplayName("Вход")
    class Login {

        @Test
        @DisplayName("верный пароль — вход по логину в любом регистре, новое устройство")
        void signsIn() throws Exception {
            // подготовка
            register("Alice", "correct horse").andExpect(status().isCreated());

            // вызов
            ResultActions result = login("  ALICE  ", "correct horse")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.login").value("alice"));

            // проверка: второе устройство аккаунта
            me(device(result)).andExpect(jsonPath("$.user.login").value("alice"));
            assertThat(jdbc.queryForObject("select count(*) from device where revoked_at is null", Integer.class))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("неверный пароль и незарегистрированный логин — одинаковая ошибка 400 INVALID_CREDENTIALS")
        void wrongCredentials() throws Exception {
            // подготовка
            register("alice", "correct horse").andExpect(status().isCreated());

            // вызов и проверка
            for (ResultActions result : new ResultActions[] {
                    login("alice", "wrong horse"), login("nobody", "whatever1")}) {
                result.andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                        .andExpect(jsonPath("$.detail").value("Неверный логин или пароль."))
                        .andExpect(cookie().doesNotExist(DeviceCookies.NAME));
            }
        }

        @Test
        @DisplayName("11-я попытка за 15 минут по одному логину — 429, даже с верным паролем")
        void bruteForceIsLimited() throws Exception {
            // подготовка
            register("alice", "correct horse").andExpect(status().isCreated());
            for (int attempt = 0; attempt < 10; attempt++) {
                login("alice", "guess-" + attempt).andExpect(status().isBadRequest());
            }

            // вызов и проверка: другой регистр того же логина считается вместе
            login("ALICE", "correct horse")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        }

        @Test
        @DisplayName("аккаунт без пароля (создан по почте) по паролю не входит")
        void legacyAccountWithoutPassword() throws Exception {
            // подготовка: логин задан, пароля нет
            jdbc.update("insert into app_user (login, email) values ('user-legacy', 'old@example.com')");

            // вызов и проверка
            login("user-legacy", "anything1").andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Nested
    @DisplayName("Логин и пароль в настройках")
    class Settings {

        @Test
        @DisplayName("смена логина — вход по новому; занятый логин — 409; удалить логин нельзя")
        void changesLogin() throws Exception {
            // подготовка
            Cookie alice = auth.login("alice");
            auth.login("bob");

            // вызов и проверка
            changeLogin(alice, "Alice_New")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.login").value("alice_new"));
            changeLogin(alice, AuthHelper.loginOf("bob"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LOGIN_TAKEN"));
            changeLogin(alice, "")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("login"));

            // проверка
            rateLimiter.reset();
            login("alice_new", AuthHelper.PASSWORD).andExpect(status().isOk());
            login(AuthHelper.loginOf("alice"), AuthHelper.PASSWORD).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("смена пароля: нужен текущий; после смены входит только новый")
        void changePassword() throws Exception {
            // подготовка
            Cookie device = auth.login("alice");

            // вызов и проверка: неверный текущий пароль
            mockMvc.perform(put("/api/v1/auth/me/password").with(xsrf()).cookie(device)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\": \"wrong-one\", \"newPassword\": \"brand new pass\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

            // вызов
            mockMvc.perform(put("/api/v1/auth/me/password").with(xsrf()).cookie(device)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\": \"test-password\", \"newPassword\": \"brand new pass\"}"))
                    .andExpect(status().isNoContent());

            // проверка
            rateLimiter.reset();
            login(AuthHelper.loginOf("alice"), AuthHelper.PASSWORD).andExpect(status().isBadRequest());
            login(AuthHelper.loginOf("alice"), "brand new pass").andExpect(status().isOk());
        }

        @Test
        @DisplayName("аккаунт по почте задаёт логин и пароль (без текущего) и затем входит")
        void legacyAccountSetsLoginAndPassword() throws Exception {
            // подготовка: старый аккаунт без пароля, с запомненным устройством
            long userId = jdbc.queryForObject(
                    "insert into app_user (login, email) values ('user-old', 'old@example.com') returning id", Long.class);
            String token = Tokens.newDeviceToken();
            jdbc.update("insert into device (id, user_id, token_hash) values (?, ?, ?)",
                    UUID.randomUUID(), userId, Tokens.sha256(token));
            Cookie device = new Cookie(DeviceCookies.NAME, token);
            me(device).andExpect(jsonPath("$.user.login").value("user-old"))
                    .andExpect(jsonPath("$.user.passwordSet").value(false));

            // вызов
            changeLogin(device, "legacy-new").andExpect(status().isOk());
            mockMvc.perform(put("/api/v1/auth/me/password").with(xsrf()).cookie(device)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\": \"first password\"}"))
                    .andExpect(status().isNoContent());

            // проверка
            me(device).andExpect(jsonPath("$.user.passwordSet").value(true));
            login("legacy-new", "first password").andExpect(status().isOk());
        }

        @Test
        @DisplayName("у гостя нет логина и пароля — 403 ACCOUNT_REQUIRED")
        void guestHasNoAccount() throws Exception {
            // подготовка
            String token = Tokens.newDeviceToken();
            jdbc.update("insert into device (id, token_hash) values (?, ?)", UUID.randomUUID(), Tokens.sha256(token));

            // вызов и проверка
            changeLogin(new Cookie(DeviceCookies.NAME, token), "guest-login")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_REQUIRED"));
        }
    }
}
