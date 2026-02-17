package com.bestproduct.dating.telegram.handler;

import com.bestproduct.dating.domain.entity.Profile;
import com.bestproduct.dating.domain.entity.User;
import com.bestproduct.dating.service.ProfileService;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class SettingsCommandHandler implements CommandHandler {

    private final UserService userService;
    private final ProfileService profileService;

    @Override
    public void handle(DatingBot bot, Update update) {
        handleAsync(bot, update); // Запускаем асинхронно
    }

    /**
     * Асинхронная обработка команды настроек
     */
    @Async("telegramBotExecutor")
    public void handleAsync(DatingBot bot, Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

        try {
            Optional<User> userOpt = userService.findByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                sendNotRegisteredMessage(bot, chatId);
                return;
            }

            User user = userOpt.get();
            Optional<Profile> profileOpt = profileService.getProfileByUserId(user.getId());

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            
            if (profileOpt.isEmpty()) {
                message.setText(
                    "⚙️ Настройки\n\n" +
                    "У вас еще нет профиля.\n\n" +
                    "📍 Отправьте свою геолокацию через скрепку (📎) в Telegram, чтобы создать профиль!");
            } else {
                Profile profile = profileOpt.get();
                StringBuilder sb = new StringBuilder();
                sb.append("⚙️ Настройки профиля\n\n");
                sb.append("📊 Текущее состояние:\n");
                sb.append("👁️ Видимость: ").append(profile.getIsVisible() ? "✅ Показывается в поиске" : "❌ Скрыт").append("\n");
                sb.append("📸 Фотографий: ").append(profile.getPhotoUrls().size()).append("/3\n");
                sb.append("✏️ Описание: ").append(profile.getBio() != null && !profile.getBio().equals("Расскажите о себе...") ? "✅ Заполнено" : "❌ Не заполнено").append("\n");
                sb.append("📍 Геопозиция: ").append(profile.getLocation() != null ? "✅ Установлена" : "❌ Не установлена").append("\n\n");
                
                sb.append("🔍 Поиск: от ближайших к дальним (без ограничения расстояния)\n\n");
                
                sb.append("👇 Выберите действие для редактирования:");
                
                message.setText(sb.toString());
                message.setReplyMarkup(KeyboardFactory.getSettingsKeyboard());
            }

            try {
                bot.execute(message);
            } catch (TelegramApiException e) {
                log.error("Error sending settings message", e);
            }
            
        } catch (Exception e) {
            log.error("Error in settings command", e);
            sendErrorMessage(bot, chatId);
        }
    }
    
    private void sendNotRegisteredMessage(DatingBot bot, Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("⚠️ Вы не зарегистрированы. Используйте /start для регистрации.");
        
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending not registered message", e);
        }
    }
    
    private void sendErrorMessage(DatingBot bot, Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("❌ Произошла ошибка. Попробуйте позже.");
        
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending error message", e);
        }
    }
}



