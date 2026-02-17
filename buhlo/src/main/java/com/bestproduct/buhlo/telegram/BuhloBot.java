package com.bestproduct.dating.telegram;

import com.bestproduct.dating.config.TelegramConfig;
import com.bestproduct.dating.service.RateLimitService;
import com.bestproduct.dating.telegram.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Основной класс Telegram бота с асинхронной обработкой
 */
@Component
@Slf4j
public class DatingBot extends TelegramLongPollingBot {

    private final TelegramConfig telegramConfig;
    private final RateLimitService rateLimitService;
    private final Map<String, CommandHandler> commandHandlers;
    private final CallbackQueryHandler callbackQueryHandler;
    private final MessageHandler messageHandler;
    private final Executor telegramBotExecutor;

    public DatingBot(TelegramConfig telegramConfig,
                   RateLimitService rateLimitService,
                   StartCommandHandler startCommandHandler,
                   ProfileCommandHandler profileCommandHandler,
                   SwipeCommandHandler swipeCommandHandler,
                   MatchesCommandHandler matchesCommandHandler,
                   EventsCommandHandler eventsCommandHandler,
                   SettingsCommandHandler settingsCommandHandler,
                   HelpCommandHandler helpCommandHandler,
                   CallbackQueryHandler callbackQueryHandler,
                   MessageHandler messageHandler,
                   @Qualifier("telegramBotExecutor") Executor telegramBotExecutor) {
        this.telegramConfig = telegramConfig;
        this.rateLimitService = rateLimitService;
        this.callbackQueryHandler = callbackQueryHandler;
        this.messageHandler = messageHandler;
        this.telegramBotExecutor = telegramBotExecutor;

        // Регистрация обработчиков команд
        this.commandHandlers = new HashMap<>();
        commandHandlers.put("/start", startCommandHandler);
        commandHandlers.put("/profile", profileCommandHandler);
        commandHandlers.put("/swipe", swipeCommandHandler);
        commandHandlers.put("/matches", matchesCommandHandler);
        commandHandlers.put("/events", eventsCommandHandler);
        commandHandlers.put("/settings", settingsCommandHandler);
        commandHandlers.put("/help", helpCommandHandler);
    }

    @Override
    public String getBotUsername() {
        return telegramConfig.getUsername();
    }

    @Override
    public String getBotToken() {
        return telegramConfig.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Асинхронная обработка в отдельном потоке
        telegramBotExecutor.execute(() -> processUpdate(update));
    }

    /**
     * Обработка обновления (выполняется асинхронно)
     */
    private void processUpdate(Update update) {
        try {
            Long userId = getUserId(update);
            
            if (userId == null) {
                log.warn("Cannot extract user ID from update");
                return;
            }

            // Rate limiting
            if (!rateLimitService.allowRequest(userId)) {
                sendRateLimitMessage(update);
                return;
            }

            // Обработка callback queries (inline buttons)
            if (update.hasCallbackQuery()) {
                callbackQueryHandler.handle(this, update);
                return;
            }

            // Обработка команд
            if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText();
                
                if (text.startsWith("/")) {
                    handleCommand(update, text);
                } else {
                    // Обработка обычных сообщений
                    messageHandler.handle(this, update);
                }
            }

            // Обработка локации
            if (update.hasMessage() && update.getMessage().hasLocation()) {
                messageHandler.handleLocation(this, update);
            }

            // Обработка фото
            if (update.hasMessage() && update.getMessage().hasPhoto()) {
                messageHandler.handlePhoto(this, update);
            }

        } catch (Exception e) {
            log.error("Error processing update", e);
            sendErrorMessage(update);
        }
    }

    /**
     * Обработка команды
     */
    private void handleCommand(Update update, String text) {
        String command = text.split(" ")[0].toLowerCase();
        
        CommandHandler handler = commandHandlers.get(command);
        if (handler != null) {
            handler.handle(this, update);
        } else {
            sendUnknownCommandMessage(update);
        }
    }

    /**
     * Извлечь ID пользователя из update
     */
    private Long getUserId(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getFrom().getId();
        } else if (update.hasCallbackQuery()) {
            return update.getCallbackQuery().getFrom().getId();
        }
        return null;
    }

    /**
     * Отправить сообщение о превышении лимита запросов
     */
    private void sendRateLimitMessage(Update update) {
        Long chatId = getChatId(update);
        if (chatId != null) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("⚠️ Слишком много запросов. Пожалуйста, подождите немного.");
            
            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Error sending rate limit message", e);
            }
        }
    }

    /**
     * Отправить сообщение об ошибке
     */
    private void sendErrorMessage(Update update) {
        Long chatId = getChatId(update);
        if (chatId != null) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("❌ Произошла ошибка. Попробуйте позже или обратитесь в поддержку.");
            
            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Error sending error message", e);
            }
        }
    }

    /**
     * Отправить сообщение о неизвестной команде
     */
    private void sendUnknownCommandMessage(Update update) {
        Long chatId = getChatId(update);
        if (chatId != null) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("❓ Неизвестная команда. Используйте /help для списка доступных команд.");
            
            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Error sending unknown command message", e);
            }
        }
    }

    /**
     * Извлечь chat ID из update
     */
    private Long getChatId(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        } else if (update.hasCallbackQuery()) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return null;
    }
}



