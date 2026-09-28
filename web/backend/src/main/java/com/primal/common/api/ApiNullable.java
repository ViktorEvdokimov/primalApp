package com.primal.common.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Поле ответа API может быть {@code null}. Остальные поля в контракте OpenAPI обязательны и не {@code null}:
 * Jackson пишет все поля записи (doc/qa.md, решение 33).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface ApiNullable {
}
