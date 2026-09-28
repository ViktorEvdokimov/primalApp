package com.primal.common.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Контракт OpenAPI для генерации клиента фронтенда (orval, профиль dev). Поля схем обязательны, кроме
 * полей запросов с {@link ApiOptional}; {@code null} допускается только у полей с {@link ApiNullable}.
 * Без этого сгенерированные типы TypeScript делали бы необязательным каждое поле.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

    /** Служебная пометка необязательного поля: снимается, когда собран список {@code required}. */
    private static final String OPTIONAL = "x-primal-optional";

    @Bean
    OpenAPI primalOpenApi() {
        return new OpenAPI().info(new Info().title("Primal API").version("v1"));
    }

    @Bean
    PropertyCustomizer nullableAndOptionalProperties() {
        return (property, type) -> {
            if (property == null) {
                return null;
            }
            Schema<?> result = has(type.getCtxAnnotations(), ApiNullable.class) ? nullable(property) : property;
            if (has(type.getCtxAnnotations(), ApiOptional.class)) {
                result.addExtension(OPTIONAL, true);
            }
            return result;
        };
    }

    @Bean
    OpenApiCustomizer requiredProperties() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().values().forEach(OpenApiConfig::requireAllButOptional);
        };
    }

    @SuppressWarnings("rawtypes") // так устроены модели swagger-core
    private static void requireAllButOptional(Schema<?> schema) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null || properties.isEmpty()) {
            return;
        }
        List<String> required = properties.entrySet().stream()
                .filter(property -> !isOptional(property.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        properties.values().forEach(property -> {
            if (property.getExtensions() != null) {
                property.getExtensions().remove(OPTIONAL);
            }
        });
        schema.setRequired(required.isEmpty() ? null : required);
    }

    private static Schema<?> nullable(Schema<?> property) {
        if (property.get$ref() != null) {
            Schema<Object> reference = new Schema<>().$ref(property.get$ref());
            return new Schema<>().oneOf(List.of(reference, new Schema<>().types(Set.of("null"))));
        }
        Set<String> types = new LinkedHashSet<>(property.getTypes() == null ? Set.of() : property.getTypes());
        if (property.getType() != null) {
            types.add(property.getType());
        }
        types.add("null");
        return property.types(types);
    }

    private static boolean isOptional(Schema<?> property) {
        return property.getExtensions() != null && Boolean.TRUE.equals(property.getExtensions().get(OPTIONAL));
    }

    private static boolean has(Annotation[] annotations, Class<? extends Annotation> type) {
        if (annotations == null) {
            return false;
        }
        for (Annotation annotation : annotations) {
            if (type.isInstance(annotation)) {
                return true;
            }
        }
        return false;
    }
}
