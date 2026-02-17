package com.bestproduct.dating.telegram.util;

import com.bestproduct.dating.domain.entity.Profile;
import com.bestproduct.dating.service.ProfileService;
import com.bestproduct.dating.service.UserService;
import com.bestproduct.dating.telegram.DatingBot;
import com.bestproduct.dating.telegram.keyboard.KeyboardFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

/**
 * Утилитный класс для проверки геолокации и фото профиля пользователя
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LocationValidator {

    private final UserService userService;
    private final ProfileService profileService;

    /**
     * Проверяет, есть ли у пользователя геолокация
     * @param telegramId ID пользователя в Telegram
     * @return true если геолокация есть, false если нет
     */
    public boolean hasLocation(Long telegramId) {
        try {
            Optional<com.bestproduct.dating.domain.entity.User> userOpt = userService.findByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                return false;
            }

            Optional<Profile> profileOpt = profileService.getProfileByUserId(userOpt.get().getId());
            if (profileOpt.isEmpty()) {
                return false;
            }

            Profile profile = profileOpt.get();
            return profile.getLocation() != null;
        } catch (Exception e) {
            log.error("Error checking location for user {}", telegramId, e);
            return false;
        }
    }

    /**
     * Проверяет, есть ли у пользователя фото профиля
     * @param telegramId ID пользователя в Telegram
     * @return true если фото есть, false если нет
     */
    public boolean hasPhotos(Long telegramId) {
        try {
            Optional<com.bestproduct.dating.domain.entity.User> userOpt = userService.findByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                log.debug("User {} not found", telegramId);
                return false;
            }

            Optional<Profile> profileOpt = profileService.getProfileByUserId(userOpt.get().getId());
            if (profileOpt.isEmpty()) {
                log.debug("Profile not found for user {}", telegramId);
                return false;
            }

            Profile profile = profileOpt.get();
            boolean hasPhotos = profile.getPhotoUrls() != null && !profile.getPhotoUrls().isEmpty();
            log.debug("User {} photos check: photoUrls={}, hasPhotos={}", telegramId, profile.getPhotoUrls(), hasPhotos);
            return hasPhotos;
        } catch (Exception e) {
            log.error("Error checking photos for user {}", telegramId, e);
            return false;
        }
    }

    /**
     * Проверяет, есть ли у пользователя и геолокация, и фото
     * @param telegramId ID пользователя в Telegram
     * @return true если есть и то, и другое, false если чего-то не хватает
     */
    public boolean hasLocationAndPhotos(Long telegramId) {
        return hasLocation(telegramId) && hasPhotos(telegramId);
    }

    /**
     * Отправляет сообщение с просьбой указать геолокацию
     * @param bot Telegram бот
     * @param chatId ID чата
     * @param messageText Текст сообщения
     */
    public void sendLocationRequiredMessage(DatingBot bot, Long chatId, String messageText) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(messageText + "\n\n📍 Для использования этой функции необходимо указать ваше местоположение.\n" +
            "Отправьте геолокацию через кнопку ниже:");
        message.setReplyMarkup(KeyboardFactory.getLocationKeyboard());

        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending location required message", e);
        }
    }

    /**
     * Отправляет сообщение с просьбой загрузить фото профиля
     * @param bot Telegram бот
     * @param chatId ID чата
     * @param messageText Текст сообщения
     */
    public void sendPhotoRequiredMessage(DatingBot bot, Long chatId, String messageText) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(messageText + "\n\n📸 Для использования этой функции необходимо загрузить фото профиля.\n" +
            "Просто отправьте фото как обычное сообщение (через скрепку 📎).");

        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending photo required message", e);
        }
    }

    /**
     * Отправляет сообщение с просьбой загрузить и геолокацию, и фото
     * @param bot Telegram бот
     * @param chatId ID чата
     * @param messageText Текст сообщения
     */
    public void sendLocationAndPhotoRequiredMessage(DatingBot bot, Long chatId, String messageText) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(messageText + "\n\n📍📸 Для использования этой функции необходимо:\n" +
            "• Указать ваше местоположение (кнопка ниже)\n" +
            "• Загрузить фото профиля (отправьте фото как обычное сообщение)\n\n" +
            "Сначала укажите геолокацию:");
        message.setReplyMarkup(KeyboardFactory.getLocationAndPhotoKeyboard());

        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending location and photo required message", e);
        }
    }

    /**
     * Проверяет геолокацию и отправляет сообщение если её нет
     * @param bot Telegram бот
     * @param chatId ID чата
     * @param telegramId ID пользователя в Telegram
     * @param messageText Текст сообщения для отправки если геолокации нет
     * @return true если геолокация есть, false если нет
     */
    public boolean checkLocationAndSendMessage(DatingBot bot, Long chatId, Long telegramId, String messageText) {
        if (!hasLocation(telegramId)) {
            sendLocationRequiredMessage(bot, chatId, messageText);
            return false;
        }
        return true;
    }

    /**
     * Проверяет фото и отправляет сообщение если их нет
     * @param bot Telegram бот
     * @param chatId ID чата
     * @param telegramId ID пользователя в Telegram
     * @param messageText Текст сообщения для отправки если фото нет
     * @return true если фото есть, false если нет
     */
    public boolean checkPhotosAndSendMessage(DatingBot bot, Long chatId, Long telegramId, String messageText) {
        if (!hasPhotos(telegramId)) {
            sendPhotoRequiredMessage(bot, chatId, messageText);
            return false;
        }
        return true;
    }

    /**
     * Проверяет и геолокацию, и фото, отправляет соответствующее сообщение
     * @param bot Telegram бот
     * @param chatId ID чата
     * @param telegramId ID пользователя в Telegram
     * @param messageText Текст сообщения для отправки если чего-то не хватает
     * @return true если есть и то, и другое, false если чего-то не хватает
     */
    public boolean checkLocationAndPhotosAndSendMessage(DatingBot bot, Long chatId, Long telegramId, String messageText) {
        boolean hasLocation = hasLocation(telegramId);
        boolean hasPhotos = hasPhotos(telegramId);
        
        log.debug("User {} - hasLocation: {}, hasPhotos: {}", telegramId, hasLocation, hasPhotos);
        
        if (!hasLocation && !hasPhotos) {
            log.debug("User {} - missing both location and photos", telegramId);
            sendLocationAndPhotoRequiredMessage(bot, chatId, messageText);
            return false;
        } else if (!hasLocation) {
            log.debug("User {} - missing location only", telegramId);
            sendLocationRequiredMessage(bot, chatId, messageText);
            return false;
        } else if (!hasPhotos) {
            log.debug("User {} - missing photos only", telegramId);
            sendPhotoRequiredMessage(bot, chatId, messageText);
            return false;
        }
        
        log.debug("User {} - has both location and photos", telegramId);
        return true;
    }
}


