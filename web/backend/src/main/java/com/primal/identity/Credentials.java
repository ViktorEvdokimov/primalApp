package com.primal.identity;

import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Правила телефона (он же логин) и пароля ({@code doc/api.md} §3). Длину проверяют аннотации запросов,
 * здесь — приведение к виду для хранения и то, что аннотациями не выразить.
 */
final class Credentials {

    static final int PASSWORD_MIN = 8;
    static final int PASSWORD_MAX = 64;
    /** bcrypt учитывает только первые 72 байта пароля — длиннее не принимаем (кириллица — 2 байта на букву). */
    static final int PASSWORD_MAX_BYTES = 72;

    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s()\\-.]");
    private static final Pattern PHONE_DIGITS = Pattern.compile("\\+?\\d{10,15}");

    private Credentials() {
    }

    static boolean passwordFitsHash(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES;
    }

    /**
     * Телефон в международном формате {@code +79123456789} — так он хранится и ищется при входе. Пробелы,
     * скобки, точки и дефисы убираются; российский номер можно ввести с 8 или 7 в начале. Пусто — {@code null}.
     *
     * @throws ApiException {@code VALIDATION_FAILED} с ошибкой поля {@code phone}
     */
    static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String compact = PHONE_SEPARATORS.matcher(phone.strip()).replaceAll("");
        if (!PHONE_DIGITS.matcher(compact).matches()) {
            throw invalidField("phone", "Телефон — от 10 до 15 цифр, например +7 912 345-67-89");
        }
        if (compact.startsWith("+")) {
            return compact;
        }
        if (compact.length() == 11 && (compact.startsWith("8") || compact.startsWith("7"))) {
            return "+7" + compact.substring(1);
        }
        return "+" + compact;
    }

    /** Ошибка поля в том же виде, что у проверки аннотациями: {@code errors: [{field, message}]}. */
    static ApiException invalidField(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Проверьте заполнение полей.")
                .with("errors", List.of(Map.of("field", field, "message", message)));
    }
}
