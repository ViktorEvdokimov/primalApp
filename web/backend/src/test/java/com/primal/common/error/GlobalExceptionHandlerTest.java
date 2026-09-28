package com.primal.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@DisplayName("Формат ошибок API (problem+json)")
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false) // формат ошибок проверяется без фильтров безопасности
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("Ошибки фреймворка")
    class FrameworkErrors {

        @Test
        @DisplayName("неизвестный путь → 404 NOT_FOUND")
        void unknownPathIsNotFound() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/api/v1/nope"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.type").value("https://primal.app/problems/not-found"));
        }

        @Test
        @DisplayName("неподдерживаемый метод → 405 METHOD_NOT_ALLOWED")
        void wrongMethodIsNotAllowed() throws Exception {
            // вызов и проверка
            mockMvc.perform(post("/test/api-exception"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("неразборчивый JSON → 400 BAD_REQUEST")
        void malformedJsonIsBadRequest() throws Exception {
            // вызов и проверка
            mockMvc.perform(post("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{не json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
    }

    @Nested
    @DisplayName("Ошибки приложения")
    class ApplicationErrors {

        @Test
        @DisplayName("ошибка валидации → 400 VALIDATION_FAILED со списком полей")
        void validationErrorListsFields() throws Exception {
            // подготовка
            String body = "{\"name\": \"\"}";

            // вызов и проверка
            mockMvc.perform(post("/test/validated").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("name"))
                    .andExpect(jsonPath("$.errors[0].message").value("Введите название"));
        }

        @Test
        @DisplayName("ApiException → статус и код из ErrorCode и дополнительные поля")
        void apiExceptionKeepsCodeAndProperties() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/test/api-exception"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CODE"))
                    .andExpect(jsonPath("$.title").value("Неверный код"))
                    .andExpect(jsonPath("$.detail").value("Код не подходит"))
                    .andExpect(jsonPath("$.attemptsLeft").value(3));
        }

        @Test
        @DisplayName("непредвиденная ошибка → 500 INTERNAL_ERROR без подробностей")
        void unexpectedErrorHidesDetails() throws Exception {
            // вызов и проверка
            mockMvc.perform(get("/test/unexpected"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.detail").value("Что-то пошло не так. Попробуйте ещё раз."));
        }
    }

    record NamedRequest(@NotBlank(message = "Введите название") String name) {
    }

    /** Попадает и в контекст интеграционных тестов (сканирование пакетов) — из контракта API скрыт. */
    @Hidden
    @RestController
    static class TestController {

        @PostMapping("/test/validated")
        NamedRequest validated(@Valid @RequestBody NamedRequest request) {
            return request;
        }

        @GetMapping("/test/api-exception")
        void apiException() {
            throw new ApiException(ErrorCode.INVALID_CODE, "Код не подходит").with("attemptsLeft", 3);
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("секретная подробность");
        }
    }
}
