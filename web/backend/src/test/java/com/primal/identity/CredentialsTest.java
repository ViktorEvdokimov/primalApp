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

@DisplayName("Правила телефона (логина) и пароля")
class CredentialsTest {

    @ParameterizedTest(name = "«{0}» → {1}")
    @CsvSource(delimiter = '|', value = {
        "+7 912 345-67-89   | +79123456789",
        "8 (912) 345-67-89  | +79123456789",
        "79123456789        | +79123456789",
        "+44 20 7946 0958   | +442079460958",
        "380.44.123.45.67   | +380441234567",
    })
    @DisplayName("телефон приводится к международному формату")
    void normalizesPhone(String input, String expected) {
        // вызов и проверка
        assertThat(Credentials.normalizePhone(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("пустой телефон — телефона нет")
    void blankPhone() {
        // вызов и проверка
        assertThat(Credentials.normalizePhone(null)).isNull();
        assertThat(Credentials.normalizePhone("   ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12-34", "+7 912 abc 67 89", "+1234567890123456", "++79123456789"})
    @DisplayName("неразборчивый телефон — ошибка поля phone")
    void rejectsPhone(String input) {
        // вызов и проверка
        assertThatThrownBy(() -> Credentials.normalizePhone(input))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(error.properties().get("errors").toString()).contains("phone");
                });
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
