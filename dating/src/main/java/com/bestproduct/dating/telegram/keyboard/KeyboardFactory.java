package com.bestproduct.dating.telegram.keyboard;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Фабрика для создания клавиатур бота
 */
public class KeyboardFactory {

    /**
     * Главное меню
     */
    public static ReplyKeyboardMarkup getMainMenuKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("🔍 Искать"));
        row1.add(new KeyboardButton("💬 Мэтчи"));
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("❤️ Лайки"));
        row2.add(new KeyboardButton("👤 Профиль"));
        keyboard.add(row2);

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("🎉 События"));
        row3.add(new KeyboardButton("⚙️ Настройки"));
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    /**
     * Клавиатура регистрации
     */
    public static InlineKeyboardMarkup getRegistrationKeyboard() {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton startRegistration = new InlineKeyboardButton();
        startRegistration.setText("✅ Начать регистрацию");
        startRegistration.setCallbackData("registration_start");
        row1.add(startRegistration);
        rowsInline.add(row1);

        markupInline.setKeyboard(rowsInline);
        return markupInline;
    }

    /**
     * Клавиатура свайпов
     */
    public static InlineKeyboardMarkup getSwipeKeyboard(Long profileUserId) {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton dislike = new InlineKeyboardButton();
        dislike.setText("👎 Нет");
        dislike.setCallbackData("swipe_dislike_" + profileUserId);
        row1.add(dislike);

        InlineKeyboardButton superLike = new InlineKeyboardButton();
        superLike.setText("⭐ Супер");
        superLike.setCallbackData("swipe_superlike_" + profileUserId);
        row1.add(superLike);

        InlineKeyboardButton like = new InlineKeyboardButton();
        like.setText("❤️ Да");
        like.setCallbackData("swipe_like_" + profileUserId);
        row1.add(like);
        
        rowsInline.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton stop = new InlineKeyboardButton();
        stop.setText("⏹️ Остановить");
        stop.setCallbackData("swipe_stop");
        row2.add(stop);
        rowsInline.add(row2);

        markupInline.setKeyboard(rowsInline);
        return markupInline;
    }

    /**
     * Клавиатура списка мэтчей
     */
    public static InlineKeyboardMarkup getMatchesKeyboard(List<Long> matchIds) {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        for (Long matchId : matchIds) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText("Открыть чат");
            button.setCallbackData("match_open_" + matchId);
            row.add(button);
            rowsInline.add(row);
        }

        markupInline.setKeyboard(rowsInline);
        return markupInline;
    }

    /**
     * Клавиатура событий
     */
    public static InlineKeyboardMarkup getEventsKeyboard() {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton nearby = new InlineKeyboardButton();
        nearby.setText("🔍 Поблизости");
        nearby.setCallbackData("events_nearby");
        row1.add(nearby);
        
        InlineKeyboardButton create = new InlineKeyboardButton();
        create.setText("➕ Создать");
        create.setCallbackData("events_create");
        row1.add(create);
        rowsInline.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton myEvents = new InlineKeyboardButton();
        myEvents.setText("📋 Мои события");
        myEvents.setCallbackData("events_my");
        row2.add(myEvents);
        rowsInline.add(row2);

        markupInline.setKeyboard(rowsInline);
        return markupInline;
    }

    /**
     * Клавиатура запроса локации
     */
    public static ReplyKeyboardMarkup getLocationKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        
        KeyboardButton locationButton = new KeyboardButton();
        locationButton.setText("📍 Отправить мою локацию");
        locationButton.setRequestLocation(true);
        row.add(locationButton);
        
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    /**
     * Клавиатура выбора пола
     */
    public static InlineKeyboardMarkup getGenderKeyboard() {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton male = new InlineKeyboardButton();
        male.setText("👨 Мужской");
        male.setCallbackData("gender_MALE");
        row1.add(male);

        InlineKeyboardButton female = new InlineKeyboardButton();
        female.setText("👩 Женский");
        female.setCallbackData("gender_FEMALE");
        row1.add(female);
        
        rowsInline.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton other = new InlineKeyboardButton();
        other.setText("🌈 Другое");
        other.setCallbackData("gender_OTHER");
        row2.add(other);
        rowsInline.add(row2);

        markupInline.setKeyboard(rowsInline);
        return markupInline;
    }
    
    /**
     * Клавиатура настроек профиля
     */
    public static InlineKeyboardMarkup getSettingsKeyboard() {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton addPhoto = new InlineKeyboardButton();
        addPhoto.setText("📸 Добавить фото");
        addPhoto.setCallbackData("settings_add_photo");
        row1.add(addPhoto);
        
        InlineKeyboardButton replacePhoto = new InlineKeyboardButton();
        replacePhoto.setText("🔄 Заменить фото");
        replacePhoto.setCallbackData("settings_replace_photo");
        row1.add(replacePhoto);
        rowsInline.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton updateLocation = new InlineKeyboardButton();
        updateLocation.setText("📍 Обновить геопозицию");
        updateLocation.setCallbackData("settings_update_location");
        row2.add(updateLocation);
        rowsInline.add(row2);

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton updateBio = new InlineKeyboardButton();
        updateBio.setText("✏️ Изменить описание");
        updateBio.setCallbackData("settings_update_bio");
        row3.add(updateBio);
        rowsInline.add(row3);

        markupInline.setKeyboard(rowsInline);
        return markupInline;
    }

    /**
     * Клавиатура для запроса фото (только уведомление, без кнопки)
     */
    public static ReplyKeyboardMarkup getPhotoKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        // Возвращаем пустую клавиатуру - только уведомление
        List<KeyboardRow> keyboard = new ArrayList<>();
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    /**
     * Клавиатура для запроса и геолокации, и фото
     */
    public static ReplyKeyboardMarkup getLocationAndPhotoKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        
        // Только кнопка геолокации
        KeyboardRow locationRow = new KeyboardRow();
        KeyboardButton locationButton = new KeyboardButton();
        locationButton.setText("📍 Отправить мою локацию");
        locationButton.setRequestLocation(true);
        locationRow.add(locationButton);
        keyboard.add(locationRow);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
}



