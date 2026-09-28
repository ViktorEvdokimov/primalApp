package com.primal.support;

import com.primal.common.ratelimit.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Базовый класс интеграционных тестов: приложение целиком, MockMvc и настоящая PostgreSQL 17 в Docker.
 * Контейнер один на весь прогон тестов, Flyway применяет миграции при старте контекста. Письма
 * перехватывает {@link MailCapture}, часы можно переводить ({@link MutableClock}).
 * Нужен запущенный Docker Desktop.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({MutableClock.Config.class, MailCapture.Config.class, AuthHelper.class})
public abstract class IntegrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected MailCapture mail;

    @Autowired
    protected AuthHelper auth;

    @Autowired
    protected RateLimiter rateLimiter;

    @BeforeEach
    void resetSharedState() {
        clock.reset();
        mail.clear();
        rateLimiter.reset();
    }

    /** Удаляет пользователей, устройства и коды входа — для тестов без {@code @Transactional}. */
    protected void deleteIdentityData() {
        jdbc.update("delete from device");
        jdbc.update("delete from login_challenge");
        jdbc.update("delete from app_user");
    }
}
