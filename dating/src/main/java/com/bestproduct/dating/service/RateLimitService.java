package com.bestproduct.dating.service;

import com.bestproduct.dating.config.AppConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для rate limiting через Redis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AppConfig appConfig;
    private final Map<Long, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Проверить, может ли пользователь выполнить действие
     */
    public boolean allowRequest(Long userId) {
        Bucket bucket = getBucket(userId);
        boolean allowed = bucket.tryConsume(1);
        
        if (!allowed) {
            log.warn("Rate limit exceeded for user: {}", userId);
            incrementViolationCount(userId);
        }
        
        return allowed;
    }

    /**
     * Получить или создать bucket для пользователя
     */
    private Bucket getBucket(Long userId) {
        return buckets.computeIfAbsent(userId, id -> createNewBucket());
    }

    /**
     * Создать новый bucket с лимитами из конфигурации
     */
    private Bucket createNewBucket() {
        int requestsPerMinute = appConfig.getSecurity().getRateLimit().getRequestsPerMinute();
        
        Bandwidth limit = Bandwidth.builder()
            .capacity(requestsPerMinute)
            .refillIntervally(requestsPerMinute, Duration.ofMinutes(1))
            .build();
        
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    /**
     * Увеличить счетчик нарушений
     */
    private void incrementViolationCount(Long userId) {
        String key = "rate_limit:violations:" + userId;
        Long violations = redisTemplate.opsForValue().increment(key);
        
        // Установить TTL 24 часа
        redisTemplate.expire(key, Duration.ofHours(24));
        
        // Проверить порог для бана
        if (violations != null && violations >= appConfig.getSecurity().getRateLimit().getBanThreshold()) {
            log.error("User {} exceeded ban threshold with {} violations", userId, violations);
            // Здесь можно добавить логику автоматического бана
        }
    }

    /**
     * Получить количество нарушений пользователя
     */
    public Long getViolationCount(Long userId) {
        String key = "rate_limit:violations:" + userId;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value.toString()) : 0L;
    }

    /**
     * Сбросить счетчик нарушений
     */
    public void resetViolations(Long userId) {
        String key = "rate_limit:violations:" + userId;
        redisTemplate.delete(key);
        log.info("Violations reset for user: {}", userId);
    }

    /**
     * Очистить bucket пользователя
     */
    public void clearBucket(Long userId) {
        buckets.remove(userId);
    }
}



