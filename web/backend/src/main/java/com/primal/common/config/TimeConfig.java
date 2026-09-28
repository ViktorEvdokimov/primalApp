package com.primal.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Время и фоновые задачи. Сервисы берут текущее время из {@link Clock}: тесты подменяют часы, чтобы
 * проверить сроки кодов и устройств.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
