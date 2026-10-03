package com.primal.common.error;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/** Коды ошибок API — поле {@code code} в ответе problem+json (см. {@code doc/api.md} §1.1). */
public enum ErrorCode {

    // Общие ошибки протокола
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Некорректный запрос"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Некорректные данные"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Требуется вход"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Доступ запрещён"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Не найдено"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Метод не поддерживается"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Формат ответа не поддерживается"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Формат запроса не поддерживается"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера"),

    // Вход и доступ
    INVALID_CREDENTIALS(HttpStatus.BAD_REQUEST, "Неверный логин или пароль"),
    LOGIN_TAKEN(HttpStatus.CONFLICT, "Логин уже зарегистрирован"),
    OWNER_ONLY(HttpStatus.FORBIDDEN, "Доступно только владельцу"),
    ACCOUNT_REQUIRED(HttpStatus.FORBIDDEN, "Нужен аккаунт"),
    SHARE_LINK_INVALID(HttpStatus.NOT_FOUND, "Ссылка недействительна"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Слишком много запросов"),

    // Кампания
    VERSION_CONFLICT(HttpStatus.CONFLICT, "Состояние изменилось"),
    CHAPTER_TRANSITION_PENDING(HttpStatus.CONFLICT, "Сначала завершите переход главы"),
    CAMPAIGN_CHANGED(HttpStatus.CONFLICT, "Кампания изменилась"),
    CAMPAIGN_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_CONTENT, "Слишком много кампаний"),
    SKILL_LOCKED(HttpStatus.UNPROCESSABLE_CONTENT, "Навык недоступен"),
    NOT_ENOUGH_RESOURCES(HttpStatus.UNPROCESSABLE_CONTENT, "Недостаточно ресурсов"),
    QUEST_NOT_OPEN(HttpStatus.UNPROCESSABLE_CONTENT, "Задание не открыто"),
    QUEST_NOT_COMPLETED(HttpStatus.UNPROCESSABLE_CONTENT, "Задание не выполнено"),
    BOSS_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Босс не выбран"),
    FINAL_BOSS_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Доступен только финальный бой"),
    DECISION_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Нужно ответить на решение главы"),
    FORGE_UNAVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "Снаряжение недоступно");

    private static final String TYPE_PREFIX = "https://primal.app/problems/";

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Идентификатор типа ошибки: {@code https://primal.app/problems/version-conflict}. */
    public URI type() {
        return URI.create(TYPE_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    /** Код для ошибки, которую выбросил фреймворк, а не код приложения. */
    public static ErrorCode forStatus(int status) {
        return Arrays.stream(new ErrorCode[] {
                        BAD_REQUEST, UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, METHOD_NOT_ALLOWED,
                        NOT_ACCEPTABLE, UNSUPPORTED_MEDIA_TYPE, RATE_LIMITED})
                .filter(code -> code.status.value() == status)
                .findFirst()
                .orElse(status >= 500 ? INTERNAL_ERROR : BAD_REQUEST);
    }
}
