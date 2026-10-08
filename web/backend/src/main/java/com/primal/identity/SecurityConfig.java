package com.primal.identity;

import com.primal.common.error.ErrorCode;
import com.primal.common.error.Problems;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Безопасность ({@code doc/architecture.md} §6): запрос аутентифицирует cookie устройства, HTTP-сессий нет.
 * Без входа доступны регистрация и вход, ссылки-приглашения, каталог и проверка здоровья. CSRF — double-submit:
 * cookie {@code XSRF-TOKEN} → заголовок {@code X-XSRF-TOKEN} во всех изменяющих запросах.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC = {
        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/csrf", "/api/v1/share/**", "/api/v1/catalog/**", "/api/v1/info", "/api/v1/info/images/**",
        "/api/actuator/health", "/error",
        // описание API — только в профиле dev, где springdoc включён
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
    };

    /** Хеш пароля с префиксом алгоритма ({@code {bcrypt}…}): алгоритм можно сменить без миграции. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, DeviceService devices, DeviceCookies cookies,
                                            JsonMapper json) throws Exception {
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.spa())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .addFilterBefore(new DeviceTokenAuthenticationFilter(devices, cookies), AnonymousAuthenticationFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> write(response, json,
                                Problems.of(ErrorCode.UNAUTHENTICATED, "Войдите, чтобы продолжить.")))
                        .accessDeniedHandler((request, response, exception) -> write(response, json,
                                exception instanceof CsrfException
                                        ? Problems.of(ErrorCode.FORBIDDEN, "Запрос отклонён: нет CSRF-токена. Обновите страницу.")
                                        : Problems.of(ErrorCode.FORBIDDEN, "Доступ запрещён."))))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                .build();
    }

    private static void write(HttpServletResponse response, JsonMapper json, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), problem);
    }
}
