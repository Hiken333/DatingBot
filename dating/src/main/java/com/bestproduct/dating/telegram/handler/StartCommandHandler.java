package com.bestproduct.dating.telegram.handler;

import com.bestproduct.dating.domain.entity.User;
import com.bestproduct.dating.service.UserService;
import com.bestproduct.dating.telegram.DatingBot;
import com.bestproduct.dating.telegram.keyboard.KeyboardFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

/**
 * Обработчик команды /start
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StartCommandHandler implements CommandHandler {

    private final UserService userService;

    @Override
    public void handle(DatingBot bot, Update update) {
        handleAsync(bot, update); // Запускаем асинхронно
    }

    /**
     * Асинхронная обработка команды старт
     */
    @Async("telegramBotExecutor")
    public void handleAsync(DatingBot bot, Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        String firstName = update.getMessage().getFrom().getFirstName();

        try {
            Optional<User> existingUser = userService.findByTelegramId(telegramId);

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());

            if (existingUser.isPresent()) {
                // Пользователь уже зарегистрирован
                User user = existingUser.get();
                userService.updateLastActive(user.getId());

                message.setText(String.format(
                    "С возвращением, %s! 🍻\n\n" +
                    "Используйте меню ниже для навигации:",
                    firstName
                ));
                message.setReplyMarkup(KeyboardFactory.getMainMenuKeyboard());
            } else {
                // Новый пользователь
                message.setText(String.format(
                    "Привет, %s! 👋\n\n" +
                    "Добро пожаловать в Dating Bot - место для знакомств! 💕\n\n" +
                    "⚠️ Для использования бота вам должно быть не менее 18 лет.\n\n" +
                    "Для начала работы нам нужна информация о вас:\n" +
                    "1. Дата рождения\n" +
                    "2. Пол\n" +
                    "3. Ваше местоположение\n" +
                    "4. Фото профиля\n" +
                    "5. Информация о себе\n\n" +
                    "Начнем регистрацию? Нажмите кнопку ниже 👇",
                    firstName
                ));
                message.setReplyMarkup(KeyboardFactory.getRegistrationKeyboard());
            }

            try {
                bot.execute(message);
            } catch (TelegramApiException e) {
                log.error("Error sending start message", e);
            }
            
        } catch (Exception e) {
            log.error("Error in start command for user {}", telegramId, e);
            
            // Отправить сообщение об ошибке пользователю
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("Произошла ошибка при обработке команды. Попробуйте еще раз.");
            
            try {
                bot.execute(errorMessage);
            } catch (TelegramApiException telegramError) {
                log.error("Error sending error message", telegramError);
            }
        }
    }
}



