package com.primal.common.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Поле тела запроса можно не передавать (например, в {@code PATCH}): в контракте OpenAPI оно не входит в
 * {@code required}. Остальные поля схем обязательны (doc/qa.md, решения 33 и 56).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface ApiOptional {
}
