package com.bestproduct.dating.config;

import com.bestproduct.dating.telegram.DatingBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Конфигурация для регистрации Telegram бота
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class TelegramBotConfig {

    private final DatingBot datingBot;

    @Bean
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        
        try {
            botsApi.registerBot(datingBot);
            log.info("Telegram bot successfully registered: {}", datingBot.getBotUsername());
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot", e);
            throw e;
        }
        
        return botsApi;
    }
}



