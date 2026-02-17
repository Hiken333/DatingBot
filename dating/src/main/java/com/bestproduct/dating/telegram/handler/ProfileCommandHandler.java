package com.bestproduct.dating.telegram.handler;

import com.bestproduct.dating.config.AppConfig;
import com.bestproduct.dating.domain.entity.Profile;
import com.bestproduct.dating.domain.entity.User;
import com.bestproduct.dating.service.ProfileService;
import com.bestproduct.dating.service.UserService;
import com.bestproduct.dating.telegram.DatingBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileCommandHandler implements CommandHandler {

    private final UserService userService;
    private final ProfileService profileService;
    private final AppConfig appConfig;

    @Override
    public void handle(DatingBot bot, Update update) {
        handleAsync(bot, update); // Запускаем асинхронно
    }

    /**
     * Асинхронная обработка команды профиля
     */
    @Async("telegramBotExecutor")
    public void handleAsync(DatingBot bot, Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

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
                "У вас еще нет профиля.\n\n" +
                "📍 Отправьте геопозицию (через скрепку в Telegram), чтобы создать профиль!");
            
            try {
                bot.execute(message);
            } catch (TelegramApiException e) {
                log.error("Error sending profile message", e);
            }
        } else {
            Profile profile = profileOpt.get();
            
            // Сформировать полное описание профиля
            String profileText = formatProfile(user, profile);
            profileText += "\n\n📝 Редактирование:\n" +
                "• Фото: просто отправьте фото боту (до " + appConfig.getImages().getMaxPerProfile() + " шт)\n" +
                "• Геопозиция: отправьте новую геолокацию\n" +
                "• Настройки: используйте кнопку ⚙️ Настройки";
            
            // Отправить фотографии с описанием, если есть
            if (!profile.getPhotoUrls().isEmpty()) {
                try {
                    List<String> photoUrls = profile.getPhotoUrls();
                    
                    if (photoUrls.size() == 1) {
                        // Если только одно фото - отправить как SendPhoto
                        SendPhoto sendPhoto = new SendPhoto();
                        sendPhoto.setChatId(chatId.toString());
                        sendPhoto.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(photoUrls.get(0)));
                        sendPhoto.setCaption(profileText);
                        bot.execute(sendPhoto);
                    } else if (photoUrls.size() >= 2) {
                        // Если несколько фото - отправить как медиа-группу
                        List<InputMedia> mediaGroup = new ArrayList<>();
                        
                        for (int i = 0; i < photoUrls.size(); i++) {
                            String fileId = photoUrls.get(i);
                            InputMediaPhoto mediaPhoto = new InputMediaPhoto();
                            mediaPhoto.setMedia(fileId);
                            
                            // Добавить ПОЛНОЕ описание профиля к первому фото
                            if (i == 0) {
                                mediaPhoto.setCaption(profileText);
                            }
                            
                            mediaGroup.add(mediaPhoto);
                        }
                        
                        SendMediaGroup sendMediaGroup = new SendMediaGroup();
                        sendMediaGroup.setChatId(chatId.toString());
                        sendMediaGroup.setMedias(mediaGroup);
                        bot.execute(sendMediaGroup);
                    }
                    
                } catch (TelegramApiException e) {
                    log.error("Error sending profile photos", e);
                    // Fallback: отправить только текст
                    message.setText(profileText);
                    try {
                        bot.execute(message);
                    } catch (TelegramApiException ex) {
                        log.error("Error sending profile text fallback", ex);
                    }
                }
            } else {
                // Если фото нет - отправить только текст
                message.setText(profileText);
                
                try {
                    bot.execute(message);
                } catch (TelegramApiException e) {
                    log.error("Error sending profile text", e);
                }
            }
        }
    }

    private String formatProfile(User user, Profile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("👤 Ваш профиль\n\n");
        sb.append("Имя: ").append(user.getFirstName());
        if (user.getLastName() != null) {
            sb.append(" ").append(user.getLastName());
        }
        sb.append("\n");
        sb.append("Возраст: ").append(user.getAge()).append("\n");
        sb.append("Пол: ").append(formatGender(user.getGender())).append("\n\n");
        
        if (profile.getBio() != null) {
            sb.append("О себе: ").append(profile.getBio()).append("\n\n");
        }
        
        sb.append("Рейтинг: ").append(String.format("%.1f", profile.getRating())).append(" ⭐\n");
        sb.append("Отзывов: ").append(profile.getRatingCount()).append("\n\n");
        
        if (profile.getCity() != null) {
            sb.append("Город: ").append(profile.getCity()).append("\n");
        }
        
        sb.append("Видимость профиля: ").append(profile.getIsVisible() ? "✅ Включена" : "❌ Выключена");
        
        return sb.toString();
    }

    private String formatGender(User.Gender gender) {
        return switch (gender) {
            case MALE -> "Мужской";
            case FEMALE -> "Женский";
            case OTHER -> "Другое";
        };
    }

    private void sendNotRegisteredMessage(DatingBot bot, Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Вы не зарегистрированы. Используйте /start для регистрации.");
        
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending not registered message", e);
        }
    }
}



