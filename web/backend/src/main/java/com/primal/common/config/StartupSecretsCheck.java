package com.primal.common.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Не даёт запустить сайт по HTTPS с секретами из {@code .env.example}: если {@code PUBLIC_URL} начинается
 * с {@code https://}, а секрет короче {@value #MIN_SECRET_LENGTH} символов или содержит
 * «{@value #EXAMPLE_MARKER}», приложение останавливается с объяснением, что заменить.
 * Локально по HTTP примерные значения разрешены.
 */
@Component
public class StartupSecretsCheck implements InitializingBean {

    static final int MIN_SECRET_LENGTH = 32;
    static final String EXAMPLE_MARKER = "change-me";

    private final PrimalProperties properties;

    public StartupSecretsCheck(PrimalProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problems = findProblems(properties);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Сайт открывается по HTTPS, замените секреты в .env (doc/setup.md, раздел 3.6): "
                    + String.join("; ", problems));
        }
    }

    static List<String> findProblems(PrimalProperties properties) {
        if (!properties.isHttps()) {
            return List.of();
        }
        List<String> problems = new ArrayList<>();
        checkSecret("PRIMAL_OTP_PEPPER", properties.otp().pepper(), problems);
        checkSecret("PRIMAL_SHARE_LINK_KEY", properties.shareLink().key(), problems);
        return problems;
    }

    private static void checkSecret(String variable, String value, List<String> problems) {
        if (value.contains(EXAMPLE_MARKER)) {
            problems.add(variable + " — значение из примера");
        } else if (value.length() < MIN_SECRET_LENGTH) {
            problems.add(variable + " — короче " + MIN_SECRET_LENGTH + " символов");
        }
    }
}
