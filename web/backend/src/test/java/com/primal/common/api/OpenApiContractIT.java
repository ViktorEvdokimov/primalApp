package com.primal.common.api;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.primal.support.IntegrationTest;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Контракт OpenAPI, из которого orval генерирует клиент фронтенда (doc/qa.md, решение 33). */
@DisplayName("Контракт OpenAPI")
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class OpenApiContractIT extends IntegrationTest {

    @Test
    @DisplayName("все поля схем обязательны, null — только у полей с @ApiNullable")
    void requiredAndNullable() throws Exception {
        // вызов и проверка
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.Boss.required",
                        containsInAnyOrder("code", "name", "element", "expansion", "sortOrder", "difficulties")))
                .andExpect(jsonPath("$.components.schemas.Boss.properties.name.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.Boss.properties.element.type", hasItems("string", "null")))
                .andExpect(jsonPath("$.components.schemas.Stance.properties.toughnessPerHunter.type",
                        hasItems("integer", "null")))
                .andExpect(jsonPath("$.components.schemas.Chapter.properties.finalBattle.oneOf[0].$ref")
                        .value("#/components/schemas/BossRef"))
                .andExpect(jsonPath("$.components.schemas.Chapter.properties.finalBattle.oneOf[1].type").value("null"));
    }

    /** Контракт для CI: из этого файла перегенерируется клиент фронтенда и сверяется с закоммиченным (задача 7.2). */
    @Test
    @DisplayName("контракт сохраняется в build/openapi.json")
    void exportContract() throws Exception {
        // вызов
        String contract = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Path file = Path.of("build", "openapi.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, contract, StandardCharsets.UTF_8);

        // проверка
        assertThat(Files.size(file)).isPositive();
    }

    @Test
    @DisplayName("ответы — application/json, операции каталога — с тегом catalog")
    void mediaTypeAndTags() throws Exception {
        // вызов и проверка
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.info.title").value("Primal API"))
                .andExpect(jsonPath("$.paths['/api/v1/catalog/bosses'].get.tags[0]").value("catalog"))
                .andExpect(jsonPath("$.paths['/api/v1/catalog/bosses'].get.responses['200'].content['application/json']")
                        .exists());
    }

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlers;

    /**
     * springdoc называет схему по простому имени класса: два разных record с одним именем (например,
     * {@code Rewards} каталога и итога боя) склеиваются в одну схему, и сгенерированный клиент получает не те поля.
     */
    @Test
    @DisplayName("у разных классов тел запросов и ответов разные имена схем")
    void uniqueSchemaNames() {
        // подготовка
        Map<String, Set<Class<?>>> byName = new TreeMap<>();
        Set<Type> seen = new HashSet<>();

        // вызов
        for (HandlerMethod method : handlers.getHandlerMethods().values()) {
            collect(method.getMethod().getGenericReturnType(), byName, seen);
            for (Parameter parameter : method.getMethod().getParameters()) {
                if (parameter.isAnnotationPresent(RequestBody.class)) {
                    collect(parameter.getParameterizedType(), byName, seen);
                }
            }
        }

        // проверка
        assertThat(byName).isNotEmpty();
        byName.forEach((name, classes) -> assertThat(classes).as("схема %s", name).hasSize(1));
    }

    private static void collect(Type type, Map<String, Set<Class<?>>> byName, Set<Type> seen) {
        if (!seen.add(type)) {
            return;
        }
        switch (type) {
            case ParameterizedType parameterized -> {
                collect(parameterized.getRawType(), byName, seen);
                for (Type argument : parameterized.getActualTypeArguments()) {
                    collect(argument, byName, seen);
                }
            }
            case WildcardType wildcard -> {
                for (Type bound : wildcard.getUpperBounds()) {
                    collect(bound, byName, seen);
                }
            }
            case GenericArrayType array -> collect(array.getGenericComponentType(), byName, seen);
            case Class<?> arrayClass when arrayClass.isArray() -> collect(arrayClass.getComponentType(), byName, seen);
            case Class<?> record when record.isRecord() && record.getName().startsWith("com.primal.") -> {
                byName.computeIfAbsent(record.getSimpleName(), key -> new HashSet<>()).add(record);
                for (RecordComponent component : record.getRecordComponents()) {
                    collect(component.getGenericType(), byName, seen);
                }
            }
            default -> {
            }
        }
    }
}
