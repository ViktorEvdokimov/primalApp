package com.primal.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Часы для тестов: идут как настоящие, пока тест не остановит или не переведёт их. */
public class MutableClock extends Clock {

    private volatile Instant fixed;

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("Часы тестов работают в UTC");
    }

    @Override
    public Instant instant() {
        Instant current = fixed;
        return current == null ? Instant.now() : current;
    }

    /** Останавливает часы на текущем моменте и переводит вперёд. */
    public void advance(Duration duration) {
        fixed = instant().plus(duration);
    }

    public void set(Instant instant) {
        fixed = instant;
    }

    /** Снова настоящее время. */
    public void reset() {
        fixed = null;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }
}
