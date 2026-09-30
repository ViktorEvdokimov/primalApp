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

@DisplayName("Регистрация и вход по номеру телефона и паролю")
class PasswordAuthIT extends IntegrationTest {

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private ResultActions register(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions register(String phone, String password) throws Exception {
        return register("{\"phone\": \"" + phone + "\", \"password\": \"" + password + "\", \"displayName\": \"Игрок\"}");
    }

    private ResultActions login(String phone, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\": \"" + phone + "\", \"password\": \"" + password + "\"}"));
    }

    private ResultActions me(Cookie device) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/me").cookie(device));
    }

    private ResultActions changePhone(Cookie device, String phone) throws Exception {
        return mockMvc.perform(put("/api/v1/auth/me/phone").with(xsrf()).cookie(device)
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\": \"" + phone + "\"}"));
    }

    private static Cookie device(ResultActions result) {
        return result.andReturn().getResponse().getCookie(DeviceCookies.NAME);
    }

    @Nested
    @DisplayName("Регистрация")
    class Register {

        @Test
        @DisplayName("номер приводится к +7…, имя сохраняется, пароль — только хешем, браузер запоминается")
        void createsAccountAndDevice() throws Exception {
            // вызов
            ResultActions result = register("""
                    {"phone": "8 (912) 345-67-89", "password": "correct horse", "displayName": " Алиса "}""")
                    .andExpect(status().isCreated())
                    .andExpect(cookie().httpOnly(DeviceCookies.NAME, true))
                    .andExpect(jsonPath("$.user.phone").value("+79123456789"))
                    .andExpect(jsonPath("$.user.displayName").value("Алиса"))
                    .andExpect(jsonPath("$.user.passwordSet").value(true))
                    .andExpect(jsonPath("$.device.id").isString());

            // проверка
            String hash = jdbc.queryForObject("select password_hash from app_user where phone = '+79123456789'", String.class);
            assertThat(hash).startsWith("{bcrypt}$2").doesNotContain("correct horse");
            me(device(result)).andExpect(jsonPath("$.kind").value("USER"))
                    .andExpect(jsonPath("$.user.phone").value("+79123456789"));
        }

        @Test
        @DisplayName("номер уже зарегистрирован — 409 PHONE_TAKEN, в любом написании")
        void phoneTaken() throws Exception {
            // подготовка
            register("+7 912 345-67-89", "12345678").andExpect(status().isCreated());

            // вызов и проверка
            register("89123456789", "87654321")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PHONE_TAKEN"))
                    .andExpect(jsonPath("$.detail").value("Номер +79123456789 уже зарегистрирован. Войдите по нему."));
        }

        @Test
        @DisplayName("номер, пароль и имя обязательны и проверяются: ошибки по полям")
        void validation() throws Exception {
            // вызов и проверка: всё пусто
            register("{\"phone\": \"\", \"password\": \"\", \"displayName\": \" \"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[?(@.field == 'phone')]").exists())
                    .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
                    .andExpect(jsonPath("$.errors[?(@.field == 'displayName')].message").value("Укажите имя"));
            // короткий пароль
            register("+79123456789", "short")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].message").value("Пароль — от 8 до 64 символов"));
            // неразборчивый номер
            register("12-34", "12345678")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("phone"));
            // 40 кириллических букв — 80 байт: bcrypt столько не учитывает
            register("+79123456789", "я".repeat(40))
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
                            .content("{\"phone\": \"+79001112233\", \"password\": \"12345678\", \"displayName\": \"Вадим\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.device.id").value(guestId.toString()));

            // проверка
            Long owner = jdbc.queryForObject("select user_id from device where id = ?", Long.class, guestId);
            assertThat(owner).isEqualTo(jdbc.queryForObject("select id from app_user where phone = '+79001112233'", Long.class));
        }
    }

    @Nested
    @DisplayName("Вход")
    class Login {

        @Test
        @DisplayName("верный пароль — вход с номером в любом написании, новое устройство")
        void signsIn() throws Exception {
            // подготовка
            register("+79123456789", "correct horse").andExpect(status().isCreated());

            // вызов
            ResultActions result = login("8 912 345 67 89", "correct horse")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.phone").value("+79123456789"));

            // проверка: второе устройство аккаунта
            me(device(result)).andExpect(jsonPath("$.user.phone").value("+79123456789"));
            assertThat(jdbc.queryForObject("select count(*) from device where revoked_at is null", Integer.class))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("неверный пароль и незарегистрированный номер — одинаковая ошибка 400 INVALID_CREDENTIALS")
        void wrongCredentials() throws Exception {
            // подготовка
            register("+79123456789", "correct horse").andExpect(status().isCreated());

            // вызов и проверка
            for (ResultActions result : new ResultActions[] {
                    login("+79123456789", "wrong horse"), login("+79990000000", "whatever1")}) {
                result.andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                        .andExpect(jsonPath("$.detail").value("Неверный номер телефона или пароль."))
                        .andExpect(cookie().doesNotExist(DeviceCookies.NAME));
            }
        }

        @Test
        @DisplayName("11-я попытка за 15 минут по одному номеру — 429, даже с верным паролем")
        void bruteForceIsLimited() throws Exception {
            // подготовка
            register("+79123456789", "correct horse").andExpect(status().isCreated());
            for (int attempt = 0; attempt < 10; attempt++) {
                login("+79123456789", "guess-" + attempt).andExpect(status().isBadRequest());
            }

            // вызов и проверка: другое написание того же номера считается вместе
            login("8 912 345-67-89", "correct horse")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        }

        @Test
        @DisplayName("аккаунт без пароля (создан по почте) по паролю не входит")
        void legacyAccountWithoutPassword() throws Exception {
            // подготовка: номер задан, пароля нет
            jdbc.update("insert into app_user (phone, email) values ('+79123456789', 'old@example.com')");

            // вызов и проверка
            login("+79123456789", "anything1").andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Nested
    @DisplayName("Номер и пароль в настройках")
    class Settings {

        @Test
        @DisplayName("смена номера — вход по новому номеру; занятый номер — 409; удалить номер нельзя")
        void phone() throws Exception {
            // подготовка
            Cookie alice = auth.login("alice");
            auth.login("bob");

            // вызов и проверка
            changePhone(alice, "+44 20 7946 0958")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.phone").value("+442079460958"));
            changePhone(alice, AuthHelper.phoneOf("bob"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PHONE_TAKEN"));
            changePhone(alice, "")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("phone"));

            // проверка
            rateLimiter.reset();
            login("+442079460958", AuthHelper.PASSWORD).andExpect(status().isOk());
            login(AuthHelper.phoneOf("alice"), AuthHelper.PASSWORD).andExpect(status().isBadRequest());
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
            login(AuthHelper.phoneOf("alice"), AuthHelper.PASSWORD).andExpect(status().isBadRequest());
            login(AuthHelper.phoneOf("alice"), "brand new pass").andExpect(status().isOk());
        }

        @Test
        @DisplayName("аккаунт по почте задаёт номер и пароль (без текущего) и затем входит")
        void legacyAccountSetsPhoneAndPassword() throws Exception {
            // подготовка: старый аккаунт без номера и пароля, с запомненным устройством
            long userId = jdbc.queryForObject(
                    "insert into app_user (email) values ('old@example.com') returning id", Long.class);
            String token = Tokens.newDeviceToken();
            jdbc.update("insert into device (id, user_id, token_hash) values (?, ?, ?)",
                    UUID.randomUUID(), userId, Tokens.sha256(token));
            Cookie device = new Cookie(DeviceCookies.NAME, token);
            me(device).andExpect(jsonPath("$.user.phone").isEmpty())
                    .andExpect(jsonPath("$.user.passwordSet").value(false));

            // вызов
            changePhone(device, "+79001234567").andExpect(status().isOk());
            mockMvc.perform(put("/api/v1/auth/me/password").with(xsrf()).cookie(device)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\": \"first password\"}"))
                    .andExpect(status().isNoContent());

            // проверка
            me(device).andExpect(jsonPath("$.user.passwordSet").value(true));
            login("+79001234567", "first password").andExpect(status().isOk());
        }

        @Test
        @DisplayName("у гостя нет номера и пароля — 403 ACCOUNT_REQUIRED")
        void guestHasNoAccount() throws Exception {
            // подготовка
            String token = Tokens.newDeviceToken();
            jdbc.update("insert into device (id, token_hash) values (?, ?)", UUID.randomUUID(), Tokens.sha256(token));

            // вызов и проверка
            changePhone(new Cookie(DeviceCookies.NAME, token), "+79123456789")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_REQUIRED"));
        }
    }
}
