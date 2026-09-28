package com.primal.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.primal.common.web.RequestIdFilter;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@DisplayName("Номер запроса в логах")
@ExtendWith(OutputCaptureExtension.class)
class RequestIdLoggingIT extends IntegrationTest {

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    @Test
    @DisplayName("ошибка сервера пишется в лог с номером запроса из X-Request-Id")
    void errorLogHasRequestId(CapturedOutput output) throws Exception {
        // подготовка
        Cookie alice = auth.login("alice@example.com");

        // вызов
        mockMvc.perform(get("/test/unexpected").cookie(alice).header(RequestIdFilter.HEADER, "support-42"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(RequestIdFilter.HEADER, "support-42"));

        // проверка
        assertThat(output.getAll())
                .containsPattern("\\[support-42\\].*Необработанная ошибка");
    }
}
