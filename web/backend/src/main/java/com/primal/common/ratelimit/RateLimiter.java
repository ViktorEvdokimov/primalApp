package com.primal.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.primal.common.config.PrimalProperties;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.local.LocalBucketBuilder;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Ограничение частоты запросов (Bucket4j, в памяти экземпляра). Превышение — {@code 429 RATE_LIMITED} с
 * заголовком {@code Retry-After} и полем {@code retryAfter} (секунды).
 */
@Component
public class RateLimiter {

    /** Ограничения {@code doc/architecture.md} §6. */
    public enum Limit {
        /** Письмо с кодом на адрес: 1 в минуту и 5 в час. */
        CODE_PER_EMAIL,
        /** Письма с кодом с одного IP: по умолчанию 20 в час. */
        CODE_PER_IP,
        /** Проверка кода с одного IP: по умолчанию 60 в час. */
        VERIFY_PER_IP,
        /** Вход по ссылке-приглашению с одного IP: по умолчанию 30 в час. */
        JOIN_PER_IP
    }

    private final Map<Limit, List<Bandwidth>> bandwidths = new EnumMap<>(Limit.class);
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(100_000)
            .build();

    public RateLimiter(PrimalProperties properties) {
        PrimalProperties.RateLimit perIp = properties.rateLimit();
        bandwidths.put(Limit.CODE_PER_EMAIL, List.of(per(1, Duration.ofMinutes(1)), per(5, Duration.ofHours(1))));
        bandwidths.put(Limit.CODE_PER_IP, List.of(per(perIp.codesPerIpHour(), Duration.ofHours(1))));
        bandwidths.put(Limit.VERIFY_PER_IP, List.of(per(perIp.verificationsPerIpHour(), Duration.ofHours(1))));
        bandwidths.put(Limit.JOIN_PER_IP, List.of(per(perIp.joinsPerIpHour(), Duration.ofHours(1))));
    }

    /** Засчитывает запрос; при превышении бросает {@code RATE_LIMITED}. */
    public void check(Limit limit, String key) {
        Bucket bucket = buckets.get(limit.name() + ':' + key, ignored -> newBucket(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long seconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() + 999_999_999));
            throw new ApiException(ErrorCode.RATE_LIMITED, "Слишком много запросов. Повторите через " + seconds + " с.")
                    .with("retryAfter", seconds)
                    .header("Retry-After", String.valueOf(seconds));
        }
    }

    /** Забыть все счётчики — для тестов, где один адрес входит много раз. */
    public void reset() {
        buckets.invalidateAll();
    }

    private Bucket newBucket(Limit limit) {
        LocalBucketBuilder builder = Bucket.builder();
        bandwidths.get(limit).forEach(builder::addLimit);
        return builder.build();
    }

    private static Bandwidth per(long tokens, Duration period) {
        return Bandwidth.builder().capacity(tokens).refillGreedy(tokens, period).build();
    }
}
