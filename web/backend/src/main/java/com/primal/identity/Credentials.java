package com.primal.identity;

import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Правила логина и пароля ({@code doc/api.md} §3). Длину проверяют аннотации запросов, здесь — приведение
 * логина к виду для хранения и то, что аннотациями не выразить.
 */
final class Credentials {

    static final int LOGIN_MIN = 3;
    static final int LOGIN_MAX = 32;
    static final int PASSWORD_MIN = 8;
    static final int PASSWORD_MAX = 64;
    /** bcrypt учитывает только первые 72 байта пароля — длиннее не принимаем (кириллица — 2 байта на букву). */
    static final int PASSWORD_MAX_BYTES = 72;

    /** Логин: латиница, цифры, «.», «_», «-»; хранится в нижнем регистре. */
    private static final Pattern LOGIN = Pattern.compile("[a-z0-9._-]{" + LOGIN_MIN + "," + LOGIN_MAX + "}");

    private Credentials() {
    }

    static boolean passwordFitsHash(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES;
    }

    /**
     * Логин для хранения и поиска: пробелы по краям убираются, регистр приводится к нижнему.
     *
     * @throws ApiException {@code VALIDATION_FAILED} с ошибкой поля {@code login}
     */
    static String normalizeLogin(String login) {
        String normalized = login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
        if (!LOGIN.matcher(normalized).matches()) {
            throw invalidField("login", "Логин — от 3 до 32 символов: латиница, цифры, «.», «_», «-»");
        }
        return normalized;
    }

    /** Ошибка поля в том же виде, что у проверки аннотациями: {@code errors: [{field, message}]}. */
    static ApiException invalidField(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Проверьте заполнение полей.")
                .with("errors", List.of(Map.of("field", field, "message", message)));
    }
}
