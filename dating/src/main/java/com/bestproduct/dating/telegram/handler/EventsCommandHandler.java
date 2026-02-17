package com.bestproduct.dating.telegram.handler;

import com.bestproduct.dating.telegram.DatingBot;
import com.bestproduct.dating.telegram.keyboard.KeyboardFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventsCommandHandler implements CommandHandler {

    @Override
    public void handle(DatingBot bot, Update update) {
        handleAsync(bot, update); // Запускаем асинхронно
    }

    /**
     * Асинхронная обработка команды событий
     */
    @Async("telegramBotExecutor")
    public void handleAsync(DatingBot bot, Update update) {
        Long chatId = update.getMessage().getChatId();

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🎉 События\n\nНайдите собутыльников для совместных мероприятий!");
        message.setReplyMarkup(KeyboardFactory.getEventsKeyboard());

        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending events message", e);
        }
    }
}



