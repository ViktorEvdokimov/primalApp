package com.primal.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Правила логина и пароля")
class CredentialsTest {

    @ParameterizedTest(name = "«{0}» → {1}")
    @CsvSource(delimiter = '|', value = {
        "Alice       | alice",
        "  Alice     | alice",
        "a.b_c-d1    | a.b_c-d1",
        "89123456789 | 89123456789",
    })
    @DisplayName("логин приводится к нижнему регистру, пробелы по краям убираются")
    void normalizesLogin(String input, String expected) {
        // вызов и проверка
        assertThat(Credentials.normalizeLogin(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "ab", "a b", "alice!", "Алиса"})
    @DisplayName("недопустимый логин — ошибка поля login")
    void rejectsLogin(String input) {
        // вызов и проверка
        assertThatThrownBy(() -> Credentials.normalizeLogin(input))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(error.properties().get("errors").toString()).contains("login");
                });
    }

    @Test
    @DisplayName("логин длиннее 32 символов не принимается")
    void rejectsLongLogin() {
        // вызов и проверка
        assertThatThrownBy(() -> Credentials.normalizeLogin("a".repeat(33)))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("пароль длиннее 72 байт не помещается в bcrypt")
    void passwordBytes() {
        // вызов и проверка
        assertThat(Credentials.passwordFitsHash("a".repeat(72))).isTrue();
        assertThat(Credentials.passwordFitsHash("я".repeat(36))).isTrue();
        assertThat(Credentials.passwordFitsHash("я".repeat(37))).isFalse();
    }
}
