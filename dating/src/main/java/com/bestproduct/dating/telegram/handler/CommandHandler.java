package com.bestproduct.dating.telegram.handler;

import com.bestproduct.dating.telegram.DatingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Интерфейс для обработчиков команд бота
 */
public interface CommandHandler {
    void handle(DatingBot bot, Update update);
}



