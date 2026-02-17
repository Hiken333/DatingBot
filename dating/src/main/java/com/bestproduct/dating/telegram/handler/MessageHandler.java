package com.bestproduct.dating.telegram.handler;

import com.bestproduct.dating.domain.entity.Event;
import com.bestproduct.dating.domain.entity.Profile;
import com.bestproduct.dating.domain.entity.User;
import com.bestproduct.dating.service.PhotoService;
import com.bestproduct.dating.service.UserService;
import com.bestproduct.dating.telegram.DatingBot;
import com.bestproduct.dating.telegram.keyboard.KeyboardFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Обработчик обычных текстовых сообщений (с асинхронной обработкой)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageHandler {

    private final UserService userService;
    private final PhotoService photoService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final com.bestproduct.dating.service.ProfileService profileService;
    private final com.bestproduct.dating.service.EventService eventService;
    private final com.bestproduct.dating.telegram.util.LocationValidator locationValidator;
    private final com.bestproduct.dating.telegram.util.InputValidator inputValidator;
    
    // Command handlers
    private final ProfileCommandHandler profileCommandHandler;
    private final SwipeCommandHandler swipeCommandHandler;
    private final EventsCommandHandler eventsCommandHandler;
    private final MatchesCommandHandler matchesCommandHandler;
    private final LikesCommandHandler likesCommandHandler;
    private final SettingsCommandHandler settingsCommandHandler;
    private final HelpCommandHandler helpCommandHandler;

    public void handle(DatingBot bot, Update update) {
        String text = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();

        // Проверка состояния регистрации
        String registrationStep = getRegistrationStep(telegramId);
        
        if (registrationStep != null) {
            if (registrationStep.equals("waiting_bio")) {
                handleBioUpdate(bot, update, text);
                return;
            } else if (registrationStep.equals("waiting_event")) {
                handleEventCreation(bot, update, text);
                return;
            } else if (registrationStep.equals("waiting_photo_replace")) {
                // Обработка замены фото будет в handlePhoto
                return;
            } else {
                handleRegistrationInput(bot, update, text, registrationStep);
                return;
            }
        }

        // Обработка кнопок главного меню
        switch (text) {
            case "🔍 Искать":
                handleSearch(bot, update);
                break;
            case "💬 Мэтчи":
                handleMatches(bot, update);
                break;
            case "❤️ Лайки":
                handleLikes(bot, update);
                break;
            case "🎉 События":
                handleEvents(bot, update);
                break;
            case "👤 Профиль":
                handleProfile(bot, update);
                break;
            case "⚙️ Настройки":
                handleSettings(bot, update);
                break;
            case "❓ Помощь":
                handleHelp(bot, update);
                break;
            default:
                handleUnknown(bot, chatId);
        }
    }

    /**
     * Обработка геолокации (асинхронно)
     */
    @Async("telegramBotExecutor")
    public void handleLocation(DatingBot bot, Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        
        Double latitude = update.getMessage().getLocation().getLatitude();
        Double longitude = update.getMessage().getLocation().getLongitude();
        
        log.info("Location received from user {}: lat={}, lon={}", telegramId, latitude, longitude);
        
        try {
            // Найти пользователя
            Optional<User> userOpt = userService.findByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                sendMessage(bot, chatId, "⚠️ Сначала зарегистрируйтесь через /start");
                return;
            }
            
            User user = userOpt.get();
            Optional<com.bestproduct.dating.domain.entity.Profile> profileOpt = profileService.getProfileByUserId(user.getId());
            
            if (profileOpt.isEmpty()) {
                // Создать базовый профиль с локацией
                List<String> emptyPhotos = new ArrayList<>();
                profileService.createProfile(user.getId(), "Расскажите о себе...", emptyPhotos, latitude, longitude);
                
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(
                    "📍 Геопозиция сохранена!\n\n" +
                    "✅ Профиль создан\n" +
                    "📍 Местоположение установлено\n\n" +
                    "Теперь рекомендуем:\n" +
                    "1. Добавить фото (просто отправьте фото боту)\n" +
                    "2. Написать информацию о себе\n" +
                    "3. Начать поиск собутыльников! 🔍\n\n" +
                    "Используйте меню ниже:");
                message.setReplyMarkup(KeyboardFactory.getMainMenuKeyboard());
                
                try {
                    bot.execute(message);
                } catch (TelegramApiException e) {
                    log.error("Error sending location confirmation", e);
                }
            } else {
                // Обновить локацию существующего профиля (сохраняем существующий город)
                com.bestproduct.dating.domain.entity.Profile currentProfile = profileOpt.get();
                profileService.updateLocation(user.getId(), latitude, longitude, 
                    currentProfile.getCity(), currentProfile.getCountry());
                
                // Просто подтверждаем обновление геолокации без запроса города
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(
                    "📍 Геопозиция обновлена!\n\n" +
                    "✅ Местоположение успешно сохранено\n" +
                    "Теперь вы можете искать людей и события поблизости! 🔍");
                message.setReplyMarkup(KeyboardFactory.getMainMenuKeyboard());
                
                try {
                    bot.execute(message);
                } catch (TelegramApiException e) {
                    log.error("Error sending location update confirmation", e);
                }
            }
            
        } catch (Exception e) {
            log.error("Error handling location for user {}", telegramId, e);
            sendMessage(bot, chatId, "❌ Ошибка при сохранении геопозиции. Попробуйте позже.");
        }
    }

    /**
     * Обработка фото (асинхронно)
     */
    @Async("telegramBotExecutor")
    public void handlePhoto(DatingBot bot, Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        List<PhotoSize> photos = update.getMessage().getPhoto();
        String registrationStep = getRegistrationStep(telegramId);

        try {
            // Найти пользователя
            Optional<User> userOpt = userService.findByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                sendMessage(bot, chatId, "⚠️ Сначала зарегистрируйтесь через /start");
                return;
            }

            User user = userOpt.get();

            if ("waiting_photo_replace".equals(registrationStep)) {
                // Замена существующего фото
                String fileId = photoService.replacePhotoInProfile(user.getId(), photos);
                clearRegistrationStep(telegramId);
                sendMessage(bot, chatId, "🔄 Фотография успешно заменена!");
                log.info("Photo replaced: userId={}, fileId={}", user.getId(), fileId);
            } else {
                // Добавление нового фото
                handlePhotoAddition(bot, chatId, user, photos);
            }

        } catch (IllegalArgumentException e) {
            sendMessage(bot, chatId, "❌ Ошибка: " + e.getMessage());
            log.error("Error saving photo", e);
        }
    }

    /**
     * Обработка добавления фотографий с группировкой
     */
    private void handlePhotoAddition(DatingBot bot, Long chatId, User user, List<PhotoSize> photos) {
        Long userId = user.getId();
        String groupKey = "photo_group:" + userId;
        
        // Получить самую большую фотографию
        String fileId = photos.stream()
            .max((p1, p2) -> Integer.compare(p1.getFileSize(), p2.getFileSize()))
            .map(PhotoSize::getFileId)
            .orElse(null);
            
        if (fileId == null) {
            sendMessage(bot, chatId, "❌ Не удалось получить фотографию");
            return;
        }

        // Проверить, есть ли активная группа фотографий
        String existingGroup = (String) redisTemplate.opsForValue().get(groupKey);
        
        if (existingGroup == null) {
            // Обработать одну фотографию сразу
            processSinglePhoto(bot, chatId, userId, fileId);
        } else {
            // Добавить к существующей группе
            String groupData = existingGroup + "," + fileId;
            redisTemplate.opsForValue().set(groupKey, groupData, Duration.ofSeconds(1));
            
            // Отправить уведомление через 1 секунду
            scheduleGroupNotification(bot, chatId, userId, groupKey);
        }
    }

    /**
     * Обработать одну фотографию сразу
     */
    private void processSinglePhoto(DatingBot bot, Long chatId, Long userId, String fileId) {
        try {
            // Получить текущие фотографии профиля
            List<String> currentPhotos = photoService.getProfilePhotos(userId);
            int currentCount = currentPhotos.size();
            
            // Получить максимальный лимит фотографий
            int maxPhotos = 3; // Максимум 3 фотографии
            
            if (currentCount >= maxPhotos) {
                sendMessage(bot, chatId, "❌ Максимум 3 фотографии в профиле. Замените старые фото, чтобы добавить новые.");
                return;
            }
            
            // Создаем фиктивный PhotoSize объект с file_id
            List<PhotoSize> fakePhotoList = new ArrayList<>();
            PhotoSize fakePhoto = new PhotoSize();
            fakePhoto.setFileId(fileId);
            fakePhoto.setFileSize(1000); // Фиктивный размер
            fakePhotoList.add(fakePhoto);
            
            // Сохранить фотографию
            photoService.savePhotoToProfile(userId, fakePhotoList);
            
            sendMessage(bot, chatId, "✅ Фотография добавлена в профиль!");
            
        } catch (Exception e) {
            log.error("Error processing single photo for user {}: {}", userId, e.getMessage());
            sendMessage(bot, chatId, "❌ Не удалось добавить фотографию. Попробуйте позже.");
        }
    }

    /**
     * Запланировать уведомление о группе фотографий
     */
    private void scheduleGroupNotification(DatingBot bot, Long chatId, Long userId, String groupKey) {
        // Используем простую задержку через новый поток
        new Thread(() -> {
            try {
                Thread.sleep(1000); // 1 секунда задержки для группировки
                
                // Проверить, что группа все еще активна
                String groupData = (String) redisTemplate.opsForValue().get(groupKey);
                if (groupData != null) {
                    // Очистить группу
                    redisTemplate.delete(groupKey);
                    
                    // Получить текущие фотографии профиля
                    List<String> currentPhotos = photoService.getProfilePhotos(userId);
                    int currentCount = currentPhotos.size();
                    
                    // Получить максимальный лимит фотографий
                    int maxPhotos = 3; // Максимум 3 фотографии
                    
                    // Подсчитать количество новых фото в группе
                    String[] newPhotoIds = groupData.split(",");
                    int newPhotoCount = newPhotoIds.length;
                    
                    // Вычислить, сколько фотографий можно добавить
                    int availableSlots = maxPhotos - currentCount;
                    int photosToAdd = Math.min(newPhotoCount, availableSlots);
                    
                    if (photosToAdd <= 0) {
                        sendMessage(bot, chatId, "❌ Максимум 3 фотографии в профиле. Замените старые фото, чтобы добавить новые.");
                        return;
                    }
                    
                    // Добавить только допустимое количество фотографий
                    int addedCount = 0;
                    for (int i = 0; i < photosToAdd; i++) {
                        try {
                            // Создаем фиктивный PhotoSize объект с file_id
                            List<PhotoSize> fakePhotoList = new ArrayList<>();
                            PhotoSize fakePhoto = new PhotoSize();
                            fakePhoto.setFileId(newPhotoIds[i]);
                            fakePhoto.setFileSize(1000); // Фиктивный размер
                            fakePhotoList.add(fakePhoto);
                            
                            photoService.savePhotoToProfile(userId, fakePhotoList);
                            addedCount++;
                        } catch (Exception e) {
                            log.error("Error saving photo {}: {}", newPhotoIds[i], e.getMessage());
                        }
                    }
                    
                    // Отправить уведомление
                    if (addedCount == 0) {
                        sendMessage(bot, chatId, "❌ Не удалось добавить фотографии. Максимум 3 фотографии в профиле.");
                    } else if (addedCount == 1) {
                        sendMessage(bot, chatId, "✅ Фотография добавлена в профиль!");
                    } else {
                        sendMessage(bot, chatId, "✅ Добавлено " + addedCount + " фотографий в профиль!");
                        
                        // Если было отправлено больше фотографий, чем можно добавить
                        if (newPhotoCount > availableSlots) {
                            sendMessage(bot, chatId, "⚠️ Максимум 3 фотографии в профиле. Добавлено только " + addedCount + " из " + newPhotoCount + ".");
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Photo group notification interrupted", e);
            }
        }).start();
    }

    private void handleSearch(DatingBot bot, Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        
        if (!locationValidator.checkLocationAndPhotosAndSendMessage(bot, chatId, telegramId, 
            "🔍 Для поиска собутыльников необходимо указать ваше местоположение и загрузить фото профиля.")) {
            return;
        }
        
        // Используем SwipeCommandHandler для поиска
        swipeCommandHandler.handle(bot, update);
    }

    private void handleMatches(DatingBot bot, Update update) {
        // Используем MatchesCommandHandler для отображения мэтчей
        matchesCommandHandler.handle(bot, update);
    }

    private void handleLikes(DatingBot bot, Update update) {
        likesCommandHandler.handle(bot, update);
    }

    private void handleEvents(DatingBot bot, Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        
        if (!locationValidator.checkLocationAndPhotosAndSendMessage(bot, chatId, telegramId, 
            "🎉 Для просмотра событий необходимо указать ваше местоположение и загрузить фото профиля.")) {
            return;
        }
        
        // Используем EventsCommandHandler для событий
        eventsCommandHandler.handle(bot, update);
    }

    private void handleProfile(DatingBot bot, Update update) {
        // Используем ProfileCommandHandler для профиля
        profileCommandHandler.handle(bot, update);
    }

    private void handleSettings(DatingBot bot, Update update) {
        // Используем SettingsCommandHandler для настроек
        settingsCommandHandler.handle(bot, update);
    }

    private void handleHelp(DatingBot bot, Update update) {
        // Используем HelpCommandHandler для справки
        helpCommandHandler.handle(bot, update);
    }

    private void handleUnknown(DatingBot bot, Long chatId) {
        sendMessage(bot, chatId, "![1763120215743](image/MessageHandler/1763120215743.png)");
    }

    /**
     * Обработка ввода во время регистрации
     */
    private void handleRegistrationInput(DatingBot bot, Update update, String text, String step) {
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        String firstName = update.getMessage().getFrom().getFirstName();
        String lastName = update.getMessage().getFrom().getLastName();
        String username = update.getMessage().getFrom().getUserName();

        try {
            if ("waiting_birthdate".equals(step)) {
                // Обработка даты рождения
                handleBirthDateInput(bot, chatId, telegramId, text, firstName, lastName, username);
            } else if ("waiting_event_location".equals(step)) {
                // Обработка места события
                handleEventLocationInput(bot, chatId, telegramId, text);
            } else if ("waiting_event_date".equals(step)) {
                // Обработка даты события
                handleEventDateInput(bot, chatId, telegramId, text);
            } else if ("waiting_event_participants".equals(step)) {
                // Обработка количества участников
                handleEventParticipantsInput(bot, chatId, telegramId, text);
            }
        } catch (Exception e) {
            log.error("Error handling registration input", e);
            sendMessage(bot, chatId, "❌ Произошла ошибка. Попробуйте еще раз.");
        }
    }

    /**
     * Обработка ввода даты рождения
     */
    private void handleBirthDateInput(DatingBot bot, Long chatId, Long telegramId, String dateText, 
                                     String firstName, String lastName, String username) {
        try {
            // Парсинг даты
            LocalDate birthDate = LocalDate.parse(dateText, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            
            // Проверка возраста
            LocalDate minDate = LocalDate.now().minusYears(18);
            if (birthDate.isAfter(minDate)) {
                sendMessage(bot, chatId, 
                    "⚠️ Извините, но для использования бота вам должно быть не менее 18 лет.\n\n" +
                    "Попробуйте указать корректную дату рождения или обратитесь в поддержку, если считаете, что произошла ошибка.");
                return;
            }

            // Получить сохраненный пол
            String gender = getRegistrationData(telegramId, "gender");
            if (gender == null) {
                sendMessage(bot, chatId, "❌ Данные регистрации потеряны. Начните заново с /start");
                clearRegistrationStep(telegramId);
                return;
            }

            // Создать пользователя
            User.Gender userGender = User.Gender.valueOf(gender);
            User newUser = userService.createUser(telegramId, firstName, lastName, username, birthDate, userGender);
            
            // Очистить состояние регистрации
            clearRegistrationStep(telegramId);
            clearRegistrationData(telegramId, "gender");

            // Поздравить с успешной регистрацией
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(String.format(
                "🎉 Регистрация завершена!\n\n" +
                "Добро пожаловать, %s!\n" +
                "Возраст: %d лет\n" +
                "Пол: %s\n\n" +
                "Теперь рекомендуем:\n" +
                "1. 👤 Заполнить профиль (добавить фото и описание)\n" +
                "2. 📍 Указать местоположение\n" +
                "3. 🔍 Начать поиск собутыльников!\n\n" +
                "Используйте кнопки меню ниже:",
                firstName, 
                newUser.getAge(),
                formatGender(userGender)
            ));
            message.setReplyMarkup(KeyboardFactory.getMainMenuKeyboard());
            
            try {
                bot.execute(message);
            } catch (TelegramApiException e) {
                log.error("Error sending registration success message", e);
            }

        } catch (DateTimeParseException e) {
            sendMessage(bot, chatId, 
                "❌ Неверный формат даты!\n\n" +
                "Пожалуйста, используйте формат ДД.ММ.ГГГГ\n" +
                "Например: 25.12.1995\n\n" +
                "Попробуйте еще раз:");
        }
    }

    /**
     * Получить шаг регистрации
     */
    private String getRegistrationStep(Long telegramId) {
        String key = "registration:step:" + telegramId;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Установить шаг регистрации
     */
    public void setRegistrationStep(Long telegramId, String step) {
        String key = "registration:step:" + telegramId;
        redisTemplate.opsForValue().set(key, step, Duration.ofMinutes(30));
    }

    /**
     * Очистить шаг регистрации
     */
    private void clearRegistrationStep(Long telegramId) {
        String key = "registration:step:" + telegramId;
        redisTemplate.delete(key);
    }

    /**
     * Сохранить данные регистрации
     */
    public void setRegistrationData(Long telegramId, String field, String value) {
        String key = "registration:data:" + telegramId + ":" + field;
        redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(30));
    }

    /**
     * Получить данные регистрации
     */
    private String getRegistrationData(Long telegramId, String field) {
        String key = "registration:data:" + telegramId + ":" + field;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Очистить данные регистрации
     */
    private void clearRegistrationData(Long telegramId, String field) {
        String key = "registration:data:" + telegramId + ":" + field;
        redisTemplate.delete(key);
    }

    /**
     * Обработка обновления биографии
     */
    private void handleBioUpdate(DatingBot bot, Update update, String newBio) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        
        try {
            Optional<User> userOpt = userService.findByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                sendMessage(bot, chatId, "⚠️ Сначала зарегистрируйтесь через /start");
                return;
            }
            
            // Валидация длины
            if (!inputValidator.isValidLength(newBio, 1, 500)) {
                sendMessage(bot, chatId, inputValidator.getLengthErrorMessage(1, 500) + 
                    "\nВаше: " + newBio.length() + " символов");
                return;
            }
            
            // Валидация содержимого
            if (inputValidator.containsForbiddenContent(newBio)) {
                sendMessage(bot, chatId, inputValidator.getValidationErrorMessage());
                return;
            }
            
            User user = userOpt.get();
            profileService.updateBio(user.getId(), newBio);
            clearRegistrationStep(telegramId);
            
            sendMessage(bot, chatId, "✅ Описание профиля обновлено!\n\n" + 
                "Новое описание: \"" + newBio + "\"\n\n" +
                "Теперь ваш профиль будет более привлекательным! 🎉");
                
        } catch (Exception e) {
            log.error("Error updating bio for user {}", telegramId, e);
            sendMessage(bot, chatId, "❌ Ошибка при обновлении описания. Попробуйте позже.");
        }
    }
    
    /**
     * Обработка создания события
     */
    private void handleEventCreation(DatingBot bot, Update update, String eventTitle) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        
        try {
            if (eventTitle.length() < 5 || eventTitle.length() > 100) {
                sendMessage(bot, chatId, "❌ Название события должно быть от 5 до 100 символов!");
                return;
            }
            
            // Сохранить название и попросить место
            setRegistrationData(telegramId, "event_title", eventTitle);
            setRegistrationStep(telegramId, "waiting_event_location");
            
            sendMessage(bot, chatId, 
                "✅ Название: \"" + eventTitle + "\"\n\n" +
                "📍 Теперь напишите место проведения:\n" +
                "Например: \"Бар 'У Михалыча', ул. Ленина 10\" или \"Парк Сокольники\"");
                
        } catch (Exception e) {
            log.error("Error creating event for user {}", telegramId, e);
            sendMessage(bot, chatId, "❌ Ошибка при создании события. Попробуйте позже.");
        }
    }
    
    /**
     * Обработка ввода места события
     */
    private void handleEventLocationInput(DatingBot bot, Long chatId, Long telegramId, String location) {
        try {
            // Валидация длины
            if (!inputValidator.isValidLength(location, 3, 200)) {
                sendMessage(bot, chatId, inputValidator.getLengthErrorMessage(3, 200));
                return;
            }
            
            // Валидация содержимого
            if (inputValidator.containsForbiddenContent(location)) {
                sendMessage(bot, chatId, inputValidator.getValidationErrorMessage());
                return;
            }
            
            // Сохранить место и попросить дату
            setRegistrationData(telegramId, "event_location", location);
            setRegistrationStep(telegramId, "waiting_event_date");
            
            sendMessage(bot, chatId, 
                "✅ Место: \"" + location + "\"\n\n" +
                "⏰ Теперь укажите дату и время события:\n" +
                "Формат: ДД.ММ.ГГГГ ЧЧ:ММ\n" +
                "Например: \"15.12.2024 19:00\"");
                
        } catch (Exception e) {
            log.error("Error handling event location input for user {}", telegramId, e);
            sendMessage(bot, chatId, "❌ Ошибка при обработке места. Попробуйте еще раз.");
        }
    }
    
    /**
     * Обработка ввода даты события
     */
    private void handleEventDateInput(DatingBot bot, Long chatId, Long telegramId, String dateText) {
        try {
            LocalDateTime eventDate;
            
            // Попробуем разные форматы
            if (dateText.contains(" ")) {
                // Полный формат с временем
                eventDate = LocalDateTime.parse(dateText, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            } else {
                // Только дата - добавляем время по умолчанию 19:00
                LocalDate date = LocalDate.parse(dateText, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                eventDate = date.atTime(19, 0);
            }
            
            // Проверка что дата в будущем
            if (eventDate.isBefore(LocalDateTime.now())) {
                sendMessage(bot, chatId, "❌ Дата события должна быть в будущем!");
                return;
            }
            
            // Сохранить дату и попросить количество участников
            setRegistrationData(telegramId, "event_date", eventDate.toString());
            setRegistrationStep(telegramId, "waiting_event_participants");
            
            sendMessage(bot, chatId, 
                "✅ Дата: " + eventDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "\n\n" +
                "👥 Укажите максимальное количество участников (от 2 до 20):");
                
        } catch (Exception e) {
            log.error("Error handling event date input for user {}", telegramId, e);
            sendMessage(bot, chatId, "❌ Неверный формат даты! Используйте:\n" +
                "• ДД.ММ.ГГГГ (время будет 19:00)\n" +
                "• ДД.ММ.ГГГГ ЧЧ:ММ\n" +
                "Например: \"15.12.2024\" или \"15.12.2024 20:30\"");
        }
    }
    
    /**
     * Обработка ввода количества участников события
     */
    private void handleEventParticipantsInput(DatingBot bot, Long chatId, Long telegramId, String participantsText) {
        try {
            int maxParticipants = Integer.parseInt(participantsText);
            
            if (maxParticipants < 2 || maxParticipants > 20) {
                sendMessage(bot, chatId, "❌ Количество участников должно быть от 2 до 20!");
                return;
            }
            
            // Получить все данные события
            String title = getRegistrationData(telegramId, "event_title");
            String location = getRegistrationData(telegramId, "event_location");
            String dateStr = getRegistrationData(telegramId, "event_date");
            
            if (title == null || location == null || dateStr == null) {
                sendMessage(bot, chatId, "❌ Данные события потеряны. Начните создание заново.");
                clearRegistrationStep(telegramId);
                return;
            }
            
            LocalDateTime eventDate = LocalDateTime.parse(dateStr);
            
            // Найти пользователя
            Optional<User> userOpt = userService.findByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                sendMessage(bot, chatId, "❌ Пользователь не найден. Зарегистрируйтесь через /start");
                clearRegistrationStep(telegramId);
                return;
            }
            
            // Получить профиль для геолокации
            Optional<Profile> profileOpt = profileService.getProfileByUserId(userOpt.get().getId());
            if (profileOpt.isEmpty() || profileOpt.get().getLocation() == null) {
                sendMessage(bot, chatId, "❌ Необходимо указать геолокацию для создания события.");
                clearRegistrationStep(telegramId);
                return;
            }
            
            Profile profile = profileOpt.get();
            
            // Создать событие
            eventService.createEvent(
                userOpt.get().getId(),
                title,
                "Событие создано через бота", // Описание по умолчанию
                profile.getLocation(),
                location,
                profile.getCity() != null && !profile.getCity().isEmpty() ? profile.getCity() : "Не указан",
                eventDate,
                maxParticipants,
                Set.of(Profile.AlcoholPreference.BEER), // По умолчанию пиво
                Event.EventType.CASUAL_DRINKS
            );
            
            // Очистить состояние
            clearRegistrationStep(telegramId);
            clearRegistrationData(telegramId, "event_title");
            clearRegistrationData(telegramId, "event_location");
            clearRegistrationData(telegramId, "event_date");
            
            sendMessage(bot, chatId, 
                "🎉 Событие создано!\n\n" +
                "📝 " + title + "\n" +
                "📍 " + location + "\n" +
                "⏰ " + eventDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "\n" +
                "👥 До " + maxParticipants + " участников\n\n" +
                "Событие будет видно другим пользователям поблизости!");
                
        } catch (NumberFormatException e) {
            sendMessage(bot, chatId, "❌ Введите число от 2 до 20!");
        } catch (Exception e) {
            log.error("Error handling event participants input for user {}", telegramId, e);
            sendMessage(bot, chatId, "❌ Ошибка при создании события. Попробуйте позже.");
        }
    }
    
    /**
     * Форматировать пол для отображения
     */
    private String formatGender(User.Gender gender) {
        return switch (gender) {
            case MALE -> "Мужской";
            case FEMALE -> "Женский";
            case OTHER -> "Другое";
        };
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



