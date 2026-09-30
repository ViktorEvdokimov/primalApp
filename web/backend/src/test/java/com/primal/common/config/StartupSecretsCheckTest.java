package com.primal.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.primal.support.TestProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Проверка секретов при запуске")
class StartupSecretsCheckTest {

    private static final String EXAMPLE_KEY = "local-dev-share-key-change-me";
    private static final String STRONG_SECRET = "9f2c4e7a1b8d3f60c5e2a9b47d1f8c3e6a0b5d2f";

    @Nested
    @DisplayName("Сайт по HTTPS")
    class Https {

        @Test
        @DisplayName("секреты из .env.example не дают запустить приложение")
        void exampleSecretsAreRejected() {
            // подготовка
            PrimalProperties properties = properties("https://primal.example.ru", EXAMPLE_KEY);
            StartupSecretsCheck check = new StartupSecretsCheck(properties);

            // вызов и проверка
            assertThatThrownBy(check::afterPropertiesSet)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PRIMAL_SHARE_LINK_KEY — значение из примера");
        }

        @Test
        @DisplayName("секрет короче 32 символов не принимается")
        void shortSecretIsRejected() {
            // подготовка
            PrimalProperties properties = properties("https://primal.example.ru", "short-secret");

            // вызов
            List<String> problems = StartupSecretsCheck.findProblems(properties);

            // проверка
            assertThat(problems).containsExactly("PRIMAL_SHARE_LINK_KEY — короче 32 символов");
        }

        @Test
        @DisplayName("надёжные секреты разрешают запуск")
        void strongSecretsAreAccepted() {
            // подготовка
            PrimalProperties properties = properties("https://primal.example.ru", STRONG_SECRET);
            StartupSecretsCheck check = new StartupSecretsCheck(properties);

            // вызов и проверка
            assertThatCode(check::afterPropertiesSet).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Локальный запуск по HTTP")
    class Http {

        @Test
        @DisplayName("секреты из .env.example разрешены")
        void exampleSecretsAreAllowed() {
            // подготовка
            PrimalProperties properties = properties("http://localhost:8088", EXAMPLE_KEY);

            // вызов
            List<String> problems = StartupSecretsCheck.findProblems(properties);

            // проверка
            assertThat(problems).isEmpty();
        }
    }

    private static PrimalProperties properties(String publicUrl, String shareKey) {
        return TestProperties.primal(publicUrl, shareKey);
    }
}
