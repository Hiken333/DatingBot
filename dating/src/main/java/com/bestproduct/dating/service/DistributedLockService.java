package com.bestproduct.dating.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Сервис для работы с распределенными блокировками через Redis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_WAIT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Выполнить операцию с блокировкой
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> operation) {
        return executeWithLock(lockKey, DEFAULT_LOCK_TIMEOUT, DEFAULT_WAIT_TIMEOUT, operation);
    }

    /**
     * Выполнить операцию с блокировкой и заданными таймаутами
     */
    public <T> T executeWithLock(String lockKey, Duration lockTimeout, Duration waitTimeout, 
                                  Supplier<T> operation) {
        String lockValue = UUID.randomUUID().toString();
        String fullKey = "lock:" + lockKey;
        
        long waitUntil = System.currentTimeMillis() + waitTimeout.toMillis();
        boolean acquired = false;
        
        try {
            // Попытка получить блокировку с повторами
            while (System.currentTimeMillis() < waitUntil) {
                Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(fullKey, lockValue, lockTimeout);
                    
                if (Boolean.TRUE.equals(success)) {
                    acquired = true;
                    log.debug("Lock acquired: {}", lockKey);
                    break;
                }
                
                // Ждем перед следующей попыткой
                Thread.sleep(50);
            }
            
            if (!acquired) {
                throw new IllegalStateException("Could not acquire lock: " + lockKey + 
                    " within " + waitTimeout.getSeconds() + " seconds");
            }
            
            // Выполнить операцию
            return operation.get();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Lock acquisition interrupted", e);
        } finally {
            // Освободить блокировку только если она принадлежит нам
            if (acquired) {
                releaseLock(fullKey, lockValue);
            }
        }
    }

    /**
     * Выполнить операцию с блокировкой (void)
     */
    public void executeWithLock(String lockKey, Runnable operation) {
        executeWithLock(lockKey, DEFAULT_LOCK_TIMEOUT, DEFAULT_WAIT_TIMEOUT, () -> {
            operation.run();
            return null;
        });
    }

    /**
     * Попытаться выполнить операцию с блокировкой без ожидания
     */
    public <T> T tryExecuteWithLock(String lockKey, Duration lockTimeout, Supplier<T> operation) {
        String lockValue = UUID.randomUUID().toString();
        String fullKey = "lock:" + lockKey;
        
        Boolean success = redisTemplate.opsForValue()
            .setIfAbsent(fullKey, lockValue, lockTimeout);
            
        if (!Boolean.TRUE.equals(success)) {
            throw new IllegalStateException("Could not acquire lock immediately: " + lockKey);
        }
        
        try {
            log.debug("Lock acquired: {}", lockKey);
            return operation.get();
        } finally {
            releaseLock(fullKey, lockValue);
        }
    }

    /**
     * Освободить блокировку
     */
    private void releaseLock(String fullKey, String lockValue) {
        try {
            // Проверяем, что блокировка все еще принадлежит нам перед удалением
            Object currentValue = redisTemplate.opsForValue().get(fullKey);
            if (lockValue.equals(currentValue)) {
                redisTemplate.delete(fullKey);
                log.debug("Lock released: {}", fullKey);
            }
        } catch (Exception e) {
            log.warn("Error releasing lock: {}", fullKey, e);
        }
    }

    /**
     * Проверить, заблокирован ли ключ
     */
    public boolean isLocked(String lockKey) {
        String fullKey = "lock:" + lockKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(fullKey));
    }

    /**
     * Получить имя блокировки для пользователя
     */
    public static String userLockKey(Long userId) {
        return "user:" + userId;
    }

    /**
     * Получить имя блокировки для матча
     */
    public static String matchLockKey(Long userId1, Long userId2) {
        // Сортируем ID для консистентности
        long min = Math.min(userId1, userId2);
        long max = Math.max(userId1, userId2);
        return "match:" + min + ":" + max;
    }

    /**
     * Получить имя блокировки для события
     */
    public static String eventLockKey(Long eventId) {
        return "event:" + eventId;
    }

    /**
     * Получить имя блокировки для профиля
     */
    public static String profileLockKey(Long userId) {
        return "profile:" + userId;
    }
}



