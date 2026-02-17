package com.bestproduct.dating.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Утилиты для работы с асинхронными операциями
 */
@Slf4j
public class AsyncUtils {

    /**
     * Обработать результат CompletableFuture с обработкой ошибок
     */
    public static <T> CompletableFuture<T> handleAsync(
            CompletableFuture<T> future,
            Consumer<T> onSuccess,
            Consumer<Throwable> onError) {
        
        return future
            .thenApply(result -> {
                try {
                    onSuccess.accept(result);
                    return result;
                } catch (Exception e) {
                    log.error("Error in success handler", e);
                    onError.accept(e);
                    return null;
                }
            })
            .exceptionally(throwable -> {
                log.error("Error in async operation", throwable);
                onError.accept(throwable);
                return null;
            });
    }

    /**
     * Создать CompletableFuture с обработкой ошибок
     */
    public static <T> CompletableFuture<T> safeAsync(
            java.util.function.Supplier<T> supplier,
            Consumer<Throwable> onError) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                log.error("Error in async supplier", e);
                onError.accept(e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Выполнить операцию с retry при ошибке
     */
    public static <T> CompletableFuture<T> withRetry(
            java.util.function.Supplier<T> operation,
            int maxRetries,
            long delayMs) {
        
        return CompletableFuture.supplyAsync(() -> {
            int attempts = 0;
            Exception lastException = null;
            
            while (attempts < maxRetries) {
                try {
                    return operation.get();
                } catch (Exception e) {
                    lastException = e;
                    attempts++;
                    
                    if (attempts < maxRetries) {
                        log.warn("Retry attempt {}/{} after error: {}", 
                            attempts, maxRetries, e.getMessage());
                        try {
                            Thread.sleep(delayMs * attempts);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Retry interrupted", ie);
                        }
                    }
                }
            }
            
            log.error("All {} retry attempts failed", maxRetries, lastException);
            throw new RuntimeException("Operation failed after " + maxRetries + " attempts", lastException);
        });
    }

    /**
     * Обработать OptimisticLockException с retry
     */
    public static <T> T handleOptimisticLock(
            java.util.function.Supplier<T> operation,
            int maxRetries) {
        
        int attempts = 0;
        
        while (attempts < maxRetries) {
            try {
                return operation.get();
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                attempts++;
                
                if (attempts >= maxRetries) {
                    log.error("OptimisticLockException after {} attempts", maxRetries, e);
                    throw e;
                }
                
                log.warn("OptimisticLockException, retry {}/{}", attempts, maxRetries);
                
                // Небольшая задержка перед retry
                try {
                    Thread.sleep(50 * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }
        
        throw new IllegalStateException("Should not reach here");
    }

    /**
     * Безопасное выполнение с timeout
     */
    public static <T> CompletableFuture<T> withTimeout(
            CompletableFuture<T> future,
            long timeoutMs,
            T defaultValue) {
        
        return future
            .completeOnTimeout(defaultValue, timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .exceptionally(throwable -> {
                log.error("Async operation failed or timed out", throwable);
                return defaultValue;
            });
    }
}



