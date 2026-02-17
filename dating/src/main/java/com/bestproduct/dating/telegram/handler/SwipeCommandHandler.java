package com.bestproduct.dating.telegram.handler;

import com.bestproduct.dating.domain.entity.Profile;
import com.bestproduct.dating.domain.entity.User;
import com.bestproduct.dating.service.ProfileService;
import com.bestproduct.dating.service.UserService;
import com.bestproduct.dating.telegram.DatingBot;
import com.bestproduct.dating.telegram.keyboard.KeyboardFactory;
import com.bestproduct.dating.telegram.util.LocationValidator;
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
public class SwipeCommandHandler implements CommandHandler {

    private final UserService userService;
    private final ProfileService profileService;
    private final LocationValidator locationValidator;

    @Override
    public void handle(DatingBot bot, Update update) {
        handleAsync(bot, update); // Запускаем асинхронно
    }

    /**
     * Асинхронная обработка команды поиска
     */
    @Async("telegramBotExecutor")
    public void handleAsync(DatingBot bot, Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

        // Проверить геолокацию и фото
        if (!locationValidator.checkLocationAndPhotosAndSendMessage(bot, chatId, telegramId, 
            "🔍 Для поиска собутыльников необходимо указать ваше местоположение и загрузить фото профиля.")) {
            return;
        }

        try {
            // Найти пользователя
            Optional<User> userOpt = userService.findByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                sendMessage(bot, chatId, "⚠️ Сначала зарегистрируйтесь через /start");
                return;
            }

            User user = userOpt.get();

            // Асинхронно найти профили
            profileService.findNearbyProfilesAsync(
                user.getId(), 
                999999,  // Неограниченный радиус (параметр игнорируется)
                1        // Показать 1 профиль
            ).thenAcceptAsync(nearbyProfiles -> {
                try {
                    if (nearbyProfiles.isEmpty()) {
                        sendMessage(bot, chatId, "🎉 Вы посмотрели всех доступных пользователей!\n\n" +
                            "Попробуйте позже - возможно появятся новые люди или истечет время блокировки уже просмотренных.");
                        return;
                    }

                    // Показать первый профиль
                    Profile profile = nearbyProfiles.get(0);
                    
                    // Дополнительная проверка - если профиль попал в результат без фото, пропустить его
                    if (profile.getPhotoUrls() == null || profile.getPhotoUrls().isEmpty()) {
                        log.warn("Profile {} found without photos, skipping", profile.getId());
                        sendMessage(bot, chatId, "🎉 Вы посмотрели всех доступных пользователей!\n\n" +
                            "Попробуйте позже - возможно появятся новые люди или истечет время блокировки уже просмотренных.");
                        return;
                    }
                    
                    showProfile(bot, chatId, profile);
                } catch (Exception e) {
                    log.error("Error showing profile", e);
                    sendMessage(bot, chatId, "❌ Произошла ошибка при отображении профиля.");
                }
            }).exceptionally(ex -> {
                log.error("Error finding nearby profiles for user {}", user.getId(), ex);
                sendMessage(bot, chatId, "❌ Произошла ошибка при поиске. Попробуйте позже.");
                return null;
            });

        } catch (Exception e) {
            log.error("Error in swipe command", e);
            sendMessage(bot, chatId, "❌ Произошла ошибка. Попробуйте позже.");
        }
    }

    /**
     * Показать профиль пользователя с фотографиями
     */
    private void showProfile(DatingBot bot, Long chatId, Profile profile) throws TelegramApiException {
        User profileUser = profile.getUser();

        // Отправить фотографии профиля
        List<String> photoFileIds = profile.getPhotoUrls();
        
        // Сформировать описание профиля с кнопками выбора
        String caption = formatProfileCaption(profileUser, profile);
        caption += "\n\n👇 Выберите действие:";
        
        if (photoFileIds.isEmpty()) {
            // Если нет фото - отправить текст с кнопками
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("📸 У пользователя нет фотографий\n\n" + caption);
            message.setReplyMarkup(KeyboardFactory.getSwipeKeyboard(profileUser.getId()));
            bot.execute(message);
        } else {
            // Отправить все фотографии медиа-группой (альбомом)
            List<InputMedia> mediaGroup = new ArrayList<>();
            
            for (int i = 0; i < photoFileIds.size(); i++) {
                String fileId = photoFileIds.get(i);
                InputMediaPhoto mediaPhoto = new InputMediaPhoto();
                mediaPhoto.setMedia(fileId);
                
                // Добавить ПОЛНОЕ описание к первому фото
                if (i == 0) {
                    mediaPhoto.setCaption(caption);
                }
                
                mediaGroup.add(mediaPhoto);
            }
            
            if (mediaGroup.size() >= 2) {
                // Отправить группу медиа если есть 2+ фото
                SendMediaGroup sendMediaGroup = new SendMediaGroup();
                sendMediaGroup.setChatId(chatId.toString());
                sendMediaGroup.setMedias(mediaGroup);
                bot.execute(sendMediaGroup);
                
                // Отправить кнопки свайпа отдельным сообщением
                SendMessage buttonsMessage = new SendMessage();
                buttonsMessage.setChatId(chatId.toString());
                buttonsMessage.setText("👇 Ваше решение:");
                buttonsMessage.setReplyMarkup(KeyboardFactory.getSwipeKeyboard(profileUser.getId()));
                bot.execute(buttonsMessage);
            } else if (mediaGroup.size() == 1) {
                // Отправить одно фото как обычное сообщение
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(chatId.toString());
                sendPhoto.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(mediaGroup.get(0).getMedia()));
                sendPhoto.setCaption(mediaGroup.get(0).getCaption());
                sendPhoto.setReplyMarkup(KeyboardFactory.getSwipeKeyboard(profileUser.getId()));
                bot.execute(sendPhoto);
            } else {
                // Если нет фото, отправить только текст с кнопками
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(caption + "\n\n👇 Ваше решение:");
                message.setReplyMarkup(KeyboardFactory.getSwipeKeyboard(profileUser.getId()));
                bot.execute(message);
            }
        }
    }

    /**
     * Форматировать описание профиля
     */
    private String formatProfileCaption(User user, Profile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("👤 ").append(user.getFirstName()).append(", ").append(user.getAge());
        
        // Добавить город если есть
        if (profile.getCity() != null) {
            sb.append(" • ").append(profile.getCity());
        }
        sb.append("\n");
        
        // Краткое описание (max 100 символов)
        if (profile.getBio() != null && !profile.getBio().isBlank() && 
            !profile.getBio().equals("Расскажите о себе...")) {
            String bio = profile.getBio();
            if (bio.length() > 100) {
                bio = bio.substring(0, 97) + "...";
            }
            sb.append(bio).append("\n");
        }
        
        return sb.toString();
    }

    private void sendMessage(DatingBot bot, Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);

        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending message", e);
        }
    }
}



