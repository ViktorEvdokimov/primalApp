package com.primal.common.error;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ошибка приложения с кодом из {@link ErrorCode}. {@link GlobalExceptionHandler} превращает её в ответ
 * problem+json; дополнительные поля ({@code attemptsLeft}, {@code reasons}, {@code current} …) попадают
 * в тело ответа.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> properties = new LinkedHashMap<>();
    private final transient Map<String, String> headers = new LinkedHashMap<>();

    public ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ApiException with(String property, Object value) {
        properties.put(property, value);
        return this;
    }

    /** Заголовок ответа, например {@code Retry-After} для {@code 429}. */
    public ApiException header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> properties() {
        return Map.copyOf(properties);
    }

    public Map<String, String> headers() {
        return Map.copyOf(headers);
    }
}
