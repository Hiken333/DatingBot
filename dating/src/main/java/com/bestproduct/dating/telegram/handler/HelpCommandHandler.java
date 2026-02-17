package com.bestproduct.dating.telegram.handler;

import com.bestproduct.dating.telegram.DatingBot;
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
public class HelpCommandHandler implements CommandHandler {

    @Override
    public void handle(DatingBot bot, Update update) {
        handleAsync(bot, update); // Запускаем асинхронно
    }

    /**
     * Асинхронная обработка команды помощи
     */
    @Async("telegramBotExecutor")
    public void handleAsync(DatingBot bot, Update update) {
        Long chatId = update.getMessage().getChatId();

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(getHelpText());

        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending help message", e);
        }
    }

    private String getHelpText() {
        return """
            ❓ Помощь по использованию бота
            
            🔍 /swipe - Искать людей поблизости
            💬 /matches - Ваши мэтчи и чаты
            🎉 /events - События и пьянки
            👤 /profile - Ваш профиль
            ⚙️ /settings - Настройки
            ❓ /help - Эта справка
            
            📖 Как это работает:
            1. Заполните свой профиль
            2. Укажите местоположение
            3. Свайпайте профили других пользователей
            4. При взаимном лайке - это мэтч!
            5. Общайтесь и находите компанию для веселья
            
            🎊 События:
            - Создавайте свои мероприятия
            - Присоединяйтесь к существующим
            - Находите пьянки рядом с вами
            
            ⚠️ Правила:
            - Только для лиц 18+
            - Будьте вежливы
            - Не распространяйте спам
            - Не используйте оскорбления
            
            🆘 Поддержка: @dating_support
            """;
    }
}



