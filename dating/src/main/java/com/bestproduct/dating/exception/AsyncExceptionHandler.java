package com.bestproduct.dating.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Обработчик необработанных исключений в асинхронных методах
 */
@Component
@Slf4j
public class AsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    @Override
    public void handleUncaughtException(Throwable throwable, Method method, Object... params) {
        log.error("Async method {} threw uncaught exception with params: {}",
            method.getName(),
            Arrays.toString(params),
            throwable);

        // Дополнительная обработка в зависимости от типа исключения
        if (throwable instanceof IllegalArgumentException) {
            log.warn("Validation error in async method {}: {}", method.getName(), throwable.getMessage());
        } else if (throwable instanceof org.springframework.dao.DataAccessException) {
            log.error("Database error in async method {}: {}", method.getName(), throwable.getMessage());
        } else if (throwable instanceof org.telegram.telegrambots.meta.exceptions.TelegramApiException) {
            log.error("Telegram API error in async method {}: {}", method.getName(), throwable.getMessage());
        } else {
            log.error("Unexpected error in async method {}", method.getName(), throwable);
        }

        // Можно добавить отправку уведомлений администратору или в систему мониторинга
        // alertService.sendAlert("Async error in " + method.getName(), throwable);
    }
}



