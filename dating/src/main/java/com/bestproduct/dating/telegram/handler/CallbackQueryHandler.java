package com.bestproduct.dating.telegram.handler;

import com.bestproduct.dating.domain.entity.SwipeHistory;
import com.bestproduct.dating.domain.entity.Event;
import com.bestproduct.dating.domain.entity.User;
import com.bestproduct.dating.domain.entity.Profile;
import com.bestproduct.dating.repository.SwipeHistoryRepository;
import com.bestproduct.dating.service.*;
import com.bestproduct.dating.telegram.DatingBot;
import com.bestproduct.dating.telegram.keyboard.KeyboardFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Обработчик callback запросов от inline кнопок
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CallbackQueryHandler {

    private final UserService userService;
    private final MatchingService matchingService;
    private final EventService eventService;
    private final ProfileService profileService;
    private final SwipeHistoryRepository swipeHistoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final com.bestproduct.dating.telegram.util.LocationValidator locationValidator;

    public void handle(DatingBot bot, Update update) {
        handleAsync(bot, update); // Запускаем асинхронно
    }

    /**
     * Асинхронная обработка callback query
     */
    @Async("telegramBotExecutor")
    public void handleAsync(DatingBot bot, Update update) {
        String callbackData = update.getCallbackQuery().getData();
        String callbackId = update.getCallbackQuery().getId();

        try {
            if (callbackData.startsWith("swipe_")) {
                handleSwipeCallback(bot, update, callbackData);
            } else if (callbackData.startsWith("events_")) {
                handleEventsCallback(bot, update, callbackData);
            } else if (callbackData.startsWith("match_")) {
                handleMatchCallback(bot, update, callbackData);
            } else if (callbackData.startsWith("registration_")) {
                handleRegistrationCallback(bot, update, callbackData);
            } else if (callbackData.startsWith("gender_")) {
                handleGenderCallback(bot, update, callbackData);
            } else if (callbackData.startsWith("settings_")) {
                handleSettingsCallback(bot, update, callbackData);
            } else if (callbackData.startsWith("event_")) {
                handleEventCallback(bot, update, callbackData);
            }

            // Подтверждение обработки callback
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackId);
            bot.execute(answer);

        } catch (TelegramApiException e) {
            log.error("Error handling callback query", e);
        } catch (Exception e) {
            log.error("Unexpected error handling callback query", e);
        }
    }

    private void handleSwipeCallback(DatingBot bot, Update update, String callbackData) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        log.info("Swipe callback: {}", callbackData);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        
        if (callbackData.startsWith("swipe_like_")) {
            Long targetUserId = Long.parseLong(callbackData.replace("swipe_like_", ""));
            Long currentUserId = update.getCallbackQuery().getFrom().getId();
            
            try {
                Optional<User> currentUser = userService.findByTelegramId(currentUserId);
                if (currentUser.isPresent()) {
                    var result = matchingService.likeUser(currentUser.get().getId(), targetUserId, null, false);
                    if (result.isMatch()) {
                        message.setText("💖 ЭТО МЭТЧ! Поздравляем! 🎉\n\nВы понравились друг другу! Можете начать общение.");
                    } else {
                        // Сразу показать следующий профиль без промежуточного сообщения
                        showNextProfile(bot, update, currentUser.get().getId());
                        return; // Не отправляем промежуточное сообщение
                    }
                } else {
                    message.setText("⚠️ Сначала зарегистрируйтесь через /start");
                }
            } catch (IllegalArgumentException e) {
                if (e.getMessage().contains("Already swiped")) {
                    message.setText("⚠️ Вы уже оценили этого пользователя.\n\nПопробуйте найти других людей через поиск.");
                } else if (e.getMessage().contains("Daily like limit")) {
                    message.setText("⏰ Достигнут дневной лимит лайков. Попробуйте завтра!");
                } else {
                    message.setText("❌ " + e.getMessage());
                }
            } catch (Exception e) {
                log.error("Error processing like", e);
                message.setText("❌ Ошибка при отправке лайка. Попробуйте позже.");
            }
            
        } else if (callbackData.startsWith("swipe_dislike_")) {
            Long targetUserId = Long.parseLong(callbackData.replace("swipe_dislike_", ""));
            Long currentUserId = update.getCallbackQuery().getFrom().getId();
            
            try {
                Optional<User> currentUser = userService.findByTelegramId(currentUserId);
                if (currentUser.isPresent()) {
                    // Сохранить дизлайк в историю
                    saveSwipeHistory(currentUser.get().getId(), targetUserId, SwipeHistory.SwipeType.DISLIKE);
                    // Сразу показать следующий профиль без промежуточного сообщения
                    showNextProfile(bot, update, currentUser.get().getId());
                    return; // Не отправляем промежуточное сообщение
                } else {
                    message.setText("⚠️ Сначала зарегистрируйтесь через /start");
                }
            } catch (Exception e) {
                log.error("Error processing dislike", e);
                message.setText("❌ Ошибка при обработке. Попробуйте позже.");
            }
            
        } else if (callbackData.startsWith("swipe_superlike_")) {
            Long targetUserId = Long.parseLong(callbackData.replace("swipe_superlike_", ""));
            Long currentUserId = update.getCallbackQuery().getFrom().getId();
            
            try {
                Optional<User> currentUser = userService.findByTelegramId(currentUserId);
                if (currentUser.isPresent()) {
                    var result = matchingService.likeUser(currentUser.get().getId(), targetUserId, "❤️ СУПЕР ЛАЙК!", true);
                    if (result.isMatch()) {
                        message.setText("💖 ЭТО МЭТЧ от СУПЕР ЛАЙКА! Невероятно! 🌟🎉\n\nВы понравились друг другу! Можете начать общение.");
                    } else {
                        // Сразу показать следующий профиль без промежуточного сообщения
                        showNextProfile(bot, update, currentUser.get().getId());
                        return; // Не отправляем промежуточное сообщение
                    }
                } else {
                    message.setText("⚠️ Сначала зарегистрируйтесь через /start");
                }
            } catch (IllegalArgumentException e) {
                if (e.getMessage().contains("Already swiped")) {
                    message.setText("⚠️ Вы уже оценили этого пользователя. Ищем следующий профиль...");
                } else if (e.getMessage().contains("Daily like limit")) {
                    message.setText("⏰ Достигнут дневной лимит лайков. Попробуйте завтра!");
                } else {
                    message.setText("❌ " + e.getMessage());
                }
            } catch (Exception e) {
                log.error("Error processing super like", e);
                message.setText("❌ Ошибка при отправке супер лайка. Попробуйте позже.");
            }
        } else if ("swipe_stop".equals(callbackData)) {
            message.setText("⏹️ Поиск остановлен. Используйте /swipe чтобы начать снова.");
        }
        
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error handling swipe callback", e);
        }
    }

    private void handleEventsCallback(DatingBot bot, Update update, String callbackData) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        log.info("Events callback: {}", callbackData);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        
        switch (callbackData) {
            case "events_nearby":
                try {
                    Long telegramId = update.getCallbackQuery().getFrom().getId();
                    Long eventsChatId = update.getCallbackQuery().getMessage().getChatId();
                    
                    if (!locationValidator.checkLocationAndPhotosAndSendMessage(bot, eventsChatId, telegramId, 
                        "🔍 Для поиска событий необходимо указать ваше местоположение и загрузить фото профиля.")) {
                        return;
                    }
                    
                    Optional<User> user = userService.findByTelegramId(telegramId);
                    
                    if (user.isPresent()) {
                        var events = eventService.findNearbyEvents(user.get().getId(), 10, 10);
                        if (events.isEmpty()) {
                            message.setText("😔 Поблизости событий не найдено.\n\n" +
                                "Попробуйте:\n" +
                                "• Создать свое событие\n" +
                                "• Проверить позже\n" +
                                "• Расширить радиус поиска");
                        } else {
                            StringBuilder sb = new StringBuilder("🎉 События рядом с вами:\n\n");
                            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                            List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();
                            
                            for (int i = 0; i < events.size(); i++) {
                                Event event = events.get(i);
                                sb.append("🍺 ").append(event.getTitle()).append("\n");
                                sb.append("📍 ").append(event.getLocationName()).append("\n");
                                sb.append("⏰ ").append(event.getEventDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n");
                                sb.append("👥 ").append(event.getParticipants().size()).append("/").append(event.getMaxParticipants());
                                
                                // Показываем участников, если есть
                                if (!event.getParticipants().isEmpty()) {
                                    sb.append(" (");
                                    event.getParticipants().forEach(participant -> {
                                        if (participant.getUsername() != null && !participant.getUsername().isEmpty()) {
                                            sb.append("@").append(participant.getUsername()).append(" ");
                                        } else {
                                            sb.append(participant.getFirstName()).append(" ");
                                        }
                                    });
                                    sb.append(")");
                                }
                                sb.append("\n\n");
                                
                                // Добавляем кнопку для подписки на событие (только если это не свое событие)
                                if (!event.isOrganizer(user.get().getId())) {
                                    InlineKeyboardButton subscribeButton = new InlineKeyboardButton();
                                    subscribeButton.setText("✅ Подписаться на " + event.getTitle());
                                    subscribeButton.setCallbackData("event_subscribe_" + event.getId());
                                    keyboardRows.add(List.of(subscribeButton));
                                } else {
                                    // Показываем, что это ваше событие
                                    InlineKeyboardButton ownEventButton = new InlineKeyboardButton();
                                    ownEventButton.setText("👑 Ваше событие");
                                    ownEventButton.setCallbackData("event_own_" + event.getId());
                                    keyboardRows.add(List.of(ownEventButton));
                                }
                            }
                            
                            keyboard.setKeyboard(keyboardRows);
                            message.setReplyMarkup(keyboard);
                            message.setText(sb.toString());
                        }
                    } else {
                        message.setText("⚠️ Сначала зарегистрируйтесь через /start");
                    }
                } catch (Exception e) {
                    log.error("Error finding nearby events", e);
                    message.setText("❌ Ошибка при поиске событий. Попробуйте позже.");
                }
                break;
                
            case "events_create":
                Long telegramIdEvent = update.getCallbackQuery().getFrom().getId();
                Long chatIdEvent = update.getCallbackQuery().getMessage().getChatId();
                
                if (!locationValidator.checkLocationAndPhotosAndSendMessage(bot, chatIdEvent, telegramIdEvent, 
                    "🎉 Для создания события необходимо указать ваше местоположение и загрузить фото профиля.")) {
                    return;
                }
                
                // Проверить, есть ли уже активное событие у пользователя
                Optional<User> userOpt = userService.findByTelegramId(telegramIdEvent);
                if (userOpt.isPresent()) {
                    long activeEvents = eventService.getOrganizedEvents(userOpt.get().getId()).stream()
                        .filter(event -> event.getStatus() == Event.EventStatus.UPCOMING)
                        .count();
                    
                    if (activeEvents > 0) {
                        message.setText("❌ У вас уже есть активное событие!\n\n" +
                            "Создавать можно только одно событие за раз.\n" +
                            "Отмените текущее событие или дождитесь его завершения.");
                        break;
                    }
                }
                
                setRegistrationStep(telegramIdEvent, "waiting_event");
                
                message.setText("➕ Создание события\n\n" +
                    "Напишите название события (например: \"Пиво в центре\").\n\n" +
                    "После названия я попрошу:\n" +
                    "📍 Место проведения\n" +
                    "⏰ Дату и время\n" +
                    "👥 Количество участников\n\n" +
                    "Начните с названия:");
                break;
                
            case "events_my":
                try {
                    Long telegramId = update.getCallbackQuery().getFrom().getId();
                    Optional<User> user = userService.findByTelegramId(telegramId);
                    
                    if (user.isPresent()) {
                        var organizedEvents = eventService.getOrganizedEvents(user.get().getId());
                        var participatingEvents = eventService.getUserEvents(user.get().getId());
                        
                        log.info("User {} has {} organized events and {} participating events", 
                            user.get().getId(), organizedEvents.size(), participatingEvents.size());
                        
                        StringBuilder sb = new StringBuilder("📋 Ваши события\n\n");
                        sb.append("🎯 Организованные вами: ").append(organizedEvents.size()).append("\n");
                        sb.append("🎊 Участвуете: ").append(participatingEvents.size()).append("\n\n");
                        
                        if (!organizedEvents.isEmpty()) {
                            sb.append("📝 Ваши события:\n");
                            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                            List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();
                            
                            for (Event event : organizedEvents) {
                                sb.append("• ").append(event.getTitle()).append(" (")
                                  .append(event.getParticipants().size()).append("/")
                                  .append(event.getMaxParticipants()).append(")");
                                
                                // Показываем статус события
                                if (event.getStatus() == Event.EventStatus.UPCOMING) {
                                    sb.append(" - Активно");
                                    
                                    // Добавляем кнопки для управления событием
                                    InlineKeyboardButton manageButton = new InlineKeyboardButton();
                                    manageButton.setText("⚙️ Управлять " + event.getTitle());
                                    manageButton.setCallbackData("event_manage_" + event.getId());
                                    keyboardRows.add(List.of(manageButton));
                                    
                                    InlineKeyboardButton closeButton = new InlineKeyboardButton();
                                    closeButton.setText("🔚 Закрыть " + event.getTitle());
                                    closeButton.setCallbackData("event_close_" + event.getId());
                                    keyboardRows.add(List.of(closeButton));
                                } else if (event.getStatus() == Event.EventStatus.CANCELLED) {
                                    sb.append(" - Отменено");
                                } else if (event.getStatus() == Event.EventStatus.COMPLETED) {
                                    sb.append(" - Завершено");
                                }
                                sb.append("\n");
                            }
                            
                            if (!keyboardRows.isEmpty()) {
                                keyboard.setKeyboard(keyboardRows);
                                message.setReplyMarkup(keyboard);
                            }
                        }
                        
                        message.setText(sb.toString());
                    } else {
                        message.setText("⚠️ Сначала зарегистрируйтесь через /start");
                    }
                } catch (Exception e) {
                    log.error("Error getting user events", e);
                    message.setText("📋 Ваши события\n\n" +
                        "Здесь будет список ваших созданных событий и тех, в которых вы участвуете.\n\n" +
                        "🎯 Организованные вами: 0\n" +
                        "🎊 Участвуете: 0");
                }
                break;
                
            default:
                message.setText("❓ Неизвестное действие с событиями.");
        }
        
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error handling events callback", e);
        }
    }

    private void handleMatchCallback(DatingBot bot, Update update, String callbackData) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        log.info("Match callback: {}", callbackData);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        
        if (callbackData.startsWith("match_open_")) {
            Long matchId = Long.parseLong(callbackData.replace("match_open_", ""));
            Long telegramId = update.getCallbackQuery().getFrom().getId();
            
            try {
                Optional<User> user = userService.findByTelegramId(telegramId);
                if (user.isPresent()) {
                    var matches = matchingService.getActiveMatches(user.get().getId());
                    var match = matches.stream()
                        .filter(m -> m.getId().equals(matchId))
                        .findFirst();
                        
                    if (match.isPresent()) {
                        // Загружаем пользователя в той же транзакции
                        User otherUser = userService.findById(match.get().getOtherUser(user.get().getId()).getId()).orElse(null);
                        if (otherUser != null) {
                            // Создаем кнопку для открытия чата
                            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                            List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();
                            
                            // Если у пользователя есть username, создаем прямую ссылку
                            if (otherUser.getUsername() != null && !otherUser.getUsername().isEmpty()) {
                                InlineKeyboardButton chatButton = new InlineKeyboardButton();
                                chatButton.setText("💬 Написать " + otherUser.getFirstName());
                                chatButton.setUrl("https://t.me/" + otherUser.getUsername());
                                keyboardRows.add(List.of(chatButton));
                            } else {
                                // Если username нет, показываем инструкцию
                                InlineKeyboardButton searchButton = new InlineKeyboardButton();
                                searchButton.setText("🔍 Найти в поиске");
                                searchButton.setCallbackData("search_user_" + otherUser.getId());
                                keyboardRows.add(List.of(searchButton));
                            }
                            
                            keyboard.setKeyboard(keyboardRows);
                            message.setReplyMarkup(keyboard);
                            
                            message.setText(String.format(
                                "💬 Чат с %s\n\n" +
                                "🎉 У вас мэтч! Время познакомиться поближе.\n\n" +
                                "💡 Советы для общения:\n" +
                                "• Будьте вежливы и дружелюбны\n" +
                                "• Предложите встретиться в публичном месте\n" +
                                "• Обсудите предпочтения в алкоголе 🍺\n" +
                                "• Расскажите о своих интересах\n\n" +
                                "%s",
                                otherUser.getFirstName(),
                                otherUser.getUsername() != null && !otherUser.getUsername().isEmpty() 
                                    ? "Нажмите кнопку ниже, чтобы начать общение!" 
                                    : "К сожалению, у пользователя нет публичного username. Попробуйте найти его через поиск."
                            ));
                        } else {
                            message.setText("❌ Пользователь не найден.");
                        }
                    } else {
                        message.setText("❌ Мэтч не найден или неактивен.");
                    }
                } else {
                    message.setText("⚠️ Сначала зарегистрируйтесь через /start");
                }
            } catch (Exception e) {
                log.error("Error opening match chat", e);
                message.setText("❌ Ошибка при открытии чата. Попробуйте позже.");
            }
        } else {
            message.setText("❓ Неизвестное действие с мэтчем.");
        }
        
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error handling match callback", e);
        }
    }

    private void handleRegistrationCallback(DatingBot bot, Update update, String callbackData) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        String firstName = update.getCallbackQuery().getFrom().getFirstName();
        
        log.info("Registration callback: {}", callbackData);
        
        if ("registration_start".equals(callbackData)) {
            // Начать процесс регистрации
            sendRegistrationMessage(bot, chatId, firstName);
        }
    }
    
    private void sendRegistrationMessage(DatingBot bot, Long chatId, String firstName) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(String.format(
            "Отлично, %s! 🎉\n\n" +
            "Давайте начнем с основной информации.\n\n" +
            "Сначала мне нужно знать ваш пол:\n" +
            "👨 Мужской\n" +
            "👩 Женский\n" +
            "🌈 Другое\n\n" +
            "Выберите подходящий вариант:",
            firstName
        ));
        message.setReplyMarkup(KeyboardFactory.getGenderKeyboard());
        
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending registration message", e);
        }
    }

    private void handleGenderCallback(DatingBot bot, Update update, String callbackData) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        String gender = callbackData.replace("gender_", "");
        
        log.info("Gender selected: {}", gender);
        
        // Сохранить пол в Redis
        setRegistrationData(telegramId, "gender", gender);
        
        // Установить следующий шаг регистрации
        setRegistrationStep(telegramId, "waiting_birthdate");
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(String.format(
            "Отлично! Пол: %s ✅\n\n" +
            "Теперь мне нужна ваша дата рождения.\n\n" +
            "⚠️ Напомню: для использования бота вам должно быть не менее 18 лет.\n\n" +
            "Пожалуйста, отправьте дату рождения в формате:\n" +
            "ДД.ММ.ГГГГ\n\n" +
            "Например: 25.12.1995",
            formatGenderName(gender)
        ));
        
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending gender response", e);
        }
    }
    
    /**
     * Установить шаг регистрации
     */
    private void setRegistrationStep(Long telegramId, String step) {
        String key = "registration:step:" + telegramId;
        redisTemplate.opsForValue().set(key, step, Duration.ofMinutes(30));
    }

    /**
     * Сохранить данные регистрации
     */
    private void setRegistrationData(Long telegramId, String field, String value) {
        String key = "registration:data:" + telegramId + ":" + field;
        redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(30));
    }
    
    private String formatGenderName(String gender) {
        return switch (gender) {
            case "MALE" -> "Мужской";
            case "FEMALE" -> "Женский";
            case "OTHER" -> "Другое";
            default -> gender;
        };
    }
    
    @Transactional
    private void handleEventCallback(DatingBot bot, Update update, String callbackData) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        
        log.info("Event callback: {}", callbackData);
        
        try {
            if (callbackData.startsWith("event_subscribe_")) {
                Long eventId = Long.parseLong(callbackData.replace("event_subscribe_", ""));
                
                Optional<User> user = userService.findByTelegramId(telegramId);
                if (user.isPresent()) {
                    // Подаем заявку на участие в событии
                    eventService.requestToJoinEvent(eventId, user.get().getId(), "Хочу присоединиться к событию!");
                    message.setText("✅ Заявка на участие подана!\n\n" +
                        "Организатор рассмотрит вашу заявку и уведомит о решении.");
                } else {
                    message.setText("⚠️ Сначала зарегистрируйтесь через /start");
                }
            } else if (callbackData.startsWith("event_own_")) {
                Optional<User> user = userService.findByTelegramId(telegramId);
                if (user.isPresent()) {
                    message.setText("👑 Это ваше событие!\n\n" +
                        "Вы можете управлять им через раздел 'Мои события' в меню.");
                } else {
                    message.setText("⚠️ Сначала зарегистрируйтесь через /start");
                }
            } else if (callbackData.startsWith("event_close_")) {
                Long eventId = Long.parseLong(callbackData.replace("event_close_", ""));
                
                Optional<User> user = userService.findByTelegramId(telegramId);
                if (user.isPresent()) {
                    // Закрываем событие
                    eventService.cancelEvent(eventId, user.get().getId());
                    message.setText("✅ Событие закрыто!\n\n" +
                        "Все участники получили уведомление об отмене события.");
                } else {
                    message.setText("⚠️ Сначала зарегистрируйтесь через /start");
                }
            } else if (callbackData.startsWith("event_approve_")) {
                Long requestId = Long.parseLong(callbackData.replace("event_approve_", ""));
                
                Optional<User> user = userService.findByTelegramId(telegramId);
                if (user.isPresent()) {
                    // Одобряем заявку
                    eventService.approveRequest(requestId, user.get().getId());
                    message.setText("✅ Заявка одобрена!\n\n" +
                        "Пользователь добавлен к событию и получил уведомление.");
                } else {
                    message.setText("⚠️ Сначала зарегистрируйтесь через /start");
                }
            } else if (callbackData.startsWith("event_reject_")) {
                Long requestId = Long.parseLong(callbackData.replace("event_reject_", ""));
                
                Optional<User> user = userService.findByTelegramId(telegramId);
                if (user.isPresent()) {
                    // Отклоняем заявку
                    eventService.rejectRequest(requestId, user.get().getId());
                    message.setText("❌ Заявка отклонена.\n\n" +
                        "Пользователь получил уведомление об отказе.");
                } else {
                    message.setText("⚠️ Сначала зарегистрируйтесь через /start");
                }
            } else if (callbackData.startsWith("event_kick_")) {
                // Формат: event_kick_{eventId}_{userId}
                String[] parts = callbackData.replace("event_kick_", "").split("_");
                if (parts.length == 2) {
                    Long eventId = Long.parseLong(parts[0]);
                    Long userId = Long.parseLong(parts[1]);
                    
                    Optional<User> organizer = userService.findByTelegramId(telegramId);
                    if (organizer.isPresent()) {
                        // Кикаем участника с события
                        eventService.removeParticipantFromEvent(eventId, userId, organizer.get().getId());
                        message.setText("👢 Участник исключен из события.\n\n" +
                            "Пользователь получил уведомление об исключении.");
                    } else {
                        message.setText("⚠️ Сначала зарегистрируйтесь через /start");
                    }
                } else {
                    message.setText("❌ Ошибка в данных запроса.");
                }
            } else if (callbackData.startsWith("event_manage_")) {
                Long eventId = Long.parseLong(callbackData.replace("event_manage_", ""));
                
                Optional<User> user = userService.findByTelegramId(telegramId);
                if (user.isPresent()) {
                    try {
                        var event = eventService.getOrganizedEvents(user.get().getId()).stream()
                            .filter(e -> e.getId().equals(eventId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Event not found"));
                        
                        var requests = eventService.getEventRequests(eventId, user.get().getId());
                        
                        StringBuilder sb = new StringBuilder();
                        sb.append("⚙️ Управление событием\n\n");
                        sb.append("📋 ").append(event.getTitle()).append("\n");
                        sb.append("👥 Участников: ").append(event.getParticipants().size()).append("/").append(event.getMaxParticipants()).append("\n");
                        sb.append("📅 Дата: ").append(event.getEventDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n\n");
                        
                        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();
                        
                        // Показываем заявки на участие
                        if (!requests.isEmpty()) {
                            sb.append("📝 Заявки на участие (").append(requests.size()).append("):\n\n");
                            for (var request : requests) {
                                User requester = request.getUser();
                                sb.append("👤 ").append(requester.getFirstName());
                                if (requester.getUsername() != null && !requester.getUsername().isEmpty()) {
                                    sb.append(" (@").append(requester.getUsername()).append(")");
                                }
                                sb.append("\n");
                                
                                // Показываем возраст, если есть
                                if (requester.getBirthDate() != null) {
                                    int age = java.time.LocalDate.now().getYear() - requester.getBirthDate().getYear();
                                    sb.append("🎂 Возраст: ").append(age).append(" лет\n");
                                }
                                
                                // Показываем пол
                                if (requester.getGender() != null) {
                                    String genderText = switch (requester.getGender()) {
                                        case MALE -> "👨 Мужской";
                                        case FEMALE -> "👩 Женский";
                                        case OTHER -> "🌈 Другое";
                                        default -> "❓ Не указан";
                                    };
                                    sb.append(genderText).append("\n");
                                }
                                
                                // Показываем сообщение от пользователя
                                if (request.getMessage() != null && !request.getMessage().isEmpty()) {
                                    sb.append("💬 Сообщение: \"").append(request.getMessage()).append("\"\n");
                                }
                                
                                sb.append("\n");
                                
                                // Кнопки для одобрения/отклонения
                                InlineKeyboardButton approveButton = new InlineKeyboardButton();
                                approveButton.setText("✅ Одобрить " + requester.getFirstName());
                                approveButton.setCallbackData("event_approve_" + request.getId());
                                
                                InlineKeyboardButton rejectButton = new InlineKeyboardButton();
                                rejectButton.setText("❌ Отклонить " + requester.getFirstName());
                                rejectButton.setCallbackData("event_reject_" + request.getId());
                                
                                keyboardRows.add(List.of(approveButton, rejectButton));
                            }
                        }
                        
                        // Показываем участников (кроме организатора)
                        var participants = event.getParticipants().stream()
                            .filter(p -> !p.getId().equals(user.get().getId()))
                            .toList();
                        
                        if (!participants.isEmpty()) {
                            sb.append("👥 Участники (").append(participants.size()).append("):\n");
                            for (var participant : participants) {
                                sb.append("• ").append(participant.getFirstName());
                                if (participant.getUsername() != null && !participant.getUsername().isEmpty()) {
                                    sb.append(" (@").append(participant.getUsername()).append(")");
                                }
                                sb.append("\n");
                                
                                // Кнопка для исключения участника
                                InlineKeyboardButton kickButton = new InlineKeyboardButton();
                                kickButton.setText("👢 Исключить " + participant.getFirstName());
                                kickButton.setCallbackData("event_kick_" + eventId + "_" + participant.getId());
                                keyboardRows.add(List.of(kickButton));
                            }
                        }
                        
                        // Добавляем кнопку "Назад"
                        InlineKeyboardButton backButton = new InlineKeyboardButton();
                        backButton.setText("⬅️ Назад к списку событий");
                        backButton.setCallbackData("events_my");
                        keyboardRows.add(List.of(backButton));
                        
                        if (!keyboardRows.isEmpty()) {
                            keyboard.setKeyboard(keyboardRows);
                            message.setReplyMarkup(keyboard);
                        }
                        
                        message.setText(sb.toString());
                    } catch (Exception e) {
                        log.error("Error managing event", e);
                        message.setText("❌ Ошибка при получении информации о событии.");
                    }
                } else {
                    message.setText("⚠️ Сначала зарегистрируйтесь через /start");
                }
            } else {
                message.setText("❓ Неизвестное действие с событием.");
            }
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Request already exists")) {
                message.setText("⚠️ Вы уже подали заявку на это событие!");
            } else if (e.getMessage().contains("Already a participant")) {
                message.setText("✅ Вы уже участвуете в этом событии!");
            } else if (e.getMessage().contains("Event is full")) {
                message.setText("❌ Событие переполнено. Попробуйте другое событие.");
            } else if (e.getMessage().contains("Only organizer can")) {
                message.setText("❌ Только организатор может выполнить это действие!");
            } else if (e.getMessage().contains("Event not found")) {
                message.setText("❌ Событие не найдено!");
            } else {
                message.setText("❌ " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error handling event callback", e);
            message.setText("❌ Ошибка при обработке. Попробуйте позже.");
        }
        
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending event callback response", e);
        }
    }

    private void handleSettingsCallback(DatingBot bot, Update update, String callbackData) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        
        switch (callbackData) {
            case "settings_add_photo":
                message.setText(
                    "📸 Добавление фото\n\n" +
                    "Отправьте боту фото профиля (до 3 штук).\n\n" +
                    "💡 Советы:\n" +
                    "• Используйте качественные фотографии\n" +
                    "• Покажите себя с лучшей стороны\n" +
                    "• Можно добавить несколько фото\n\n" +
                    "Просто прикрепите фото как обычное сообщение (через скрепку 📎).");
                break;
                
            case "settings_replace_photo":
                Long telegramIdReplace = update.getCallbackQuery().getFrom().getId();
                setRegistrationStep(telegramIdReplace, "waiting_photo_replace");
                message.setText(
                    "🔄 Замена фото\n\n" +
                    "Отправьте новое фото для замены текущего.\n\n" +
                    "💡 Советы:\n" +
                    "• Используйте качественные фотографии\n" +
                    "• Покажите себя с лучшей стороны\n" +
                    "• Отправьте только одно фото\n\n" +
                    "Просто прикрепите фото как обычное сообщение (через скрепку 📎).");
                break;
                
            case "settings_update_location":
                message.setText(
                    "📍 Обновление геопозиции\n\n" +
                    "Отправьте новую геолокацию:\n\n" +
                    "1. Нажмите на скрепку (📎) в Telegram\n" +
                    "2. Выберите 'Геопозиция' или 'Location'\n" +
                    "3. Отправьте текущую позицию\n\n" +
                    "Геолокация нужна для поиска людей поблизости.");
                break;
                
            case "settings_update_bio":
                Long telegramId = update.getCallbackQuery().getFrom().getId();
                setRegistrationStep(telegramId, "waiting_bio");
                
                message.setText(
                    "✏️ Изменение описания\n\n" +
                    "Напишите новый текст о себе (до 500 символов).\n\n" +
                    "💡 Что написать:\n" +
                    "• Ваши интересы и хобби\n" +
                    "• Любимые напитки\n" +
                    "• Что ищете в собутыльниках\n" +
                    "• Интересные факты о себе\n\n" +
                    "Просто напишите текст следующим сообщением!");
                break;
                
            default:
                message.setText("❓ Неизвестная настройка.");
        }
        
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error handling settings callback", e);
        }
    }
    
    /**
     * Сохранить историю свайпа
     */
    private void saveSwipeHistory(Long fromUserId, Long toUserId, SwipeHistory.SwipeType swipeType) {
        try {
            User fromUser = userService.findById(fromUserId)
                .orElseThrow(() -> new IllegalArgumentException("From user not found"));
            User toUser = userService.findById(toUserId)
                .orElseThrow(() -> new IllegalArgumentException("To user not found"));
            
            // Проверить, есть ли уже запись (не должно быть из-за unique constraint)
            Optional<SwipeHistory> existing = swipeHistoryRepository.findByFromUserIdAndToUserId(fromUserId, toUserId);
            
            if (existing.isEmpty()) {
                SwipeHistory swipeHistory = SwipeHistory.builder()
                    .fromUser(fromUser)
                    .toUser(toUser)
                    .swipeType(swipeType)
                    .build();
                    
                swipeHistoryRepository.save(swipeHistory);
                log.debug("Saved swipe history: {} -> {} ({})", fromUserId, toUserId, swipeType);
            }
        } catch (Exception e) {
            log.error("Error saving swipe history", e);
        }
    }

    /**
     * Показать следующий профиль после свайпа (асинхронно)
     */
    @Async("telegramBotExecutor")
    private void showNextProfile(DatingBot bot, Update update, Long userId) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        
        // Получить следующий профиль для показа (используем async версию)
        profileService.findNearbyProfilesAsync(userId, 10, 1)
            .thenAcceptAsync(nearbyProfiles -> {
                try {
                    if (nearbyProfiles.isEmpty()) {
                        SendMessage message = new SendMessage();
                        message.setChatId(chatId.toString());
                        message.setText("🎉 Вы посмотрели всех доступных пользователей!\n\n" +
                            "Попробуйте позже - возможно появятся новые люди или истечет время блокировки уже просмотренных.");
                        bot.execute(message);
                        return;
                    }
                    
                    Profile profile = nearbyProfiles.get(0);
                    
                    // Проверить, что у профиля есть фото
                    if (profile.getPhotoUrls() == null || profile.getPhotoUrls().isEmpty()) {
                        // Если нет фото, попробовать найти следующий профиль рекурсивно
                        showNextProfile(bot, update, userId);
                        return;
                    }
                    
                    // Показать профиль
                    showProfile(bot, chatId, profile);
                } catch (Exception e) {
                    log.error("Error showing next profile", e);
                    try {
                        SendMessage errorMessage = new SendMessage();
                        errorMessage.setChatId(chatId.toString());
                        errorMessage.setText("❌ Ошибка при поиске следующего профиля. Попробуйте снова.");
                        bot.execute(errorMessage);
                    } catch (Exception ex) {
                        log.error("Error sending error message", ex);
                    }
                }
            })
            .exceptionally(ex -> {
                log.error("Error finding nearby profiles", ex);
                try {
                    SendMessage errorMessage = new SendMessage();
                    errorMessage.setChatId(chatId.toString());
                    errorMessage.setText("❌ Ошибка при поиске профилей. Попробуйте позже.");
                    bot.execute(errorMessage);
                } catch (Exception e) {
                    log.error("Error sending error message", e);
                }
                return null;
            });
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
        
        if (photoFileIds.size() == 1) {
            // Одна фотография
            SendPhoto photo = new SendPhoto();
            photo.setChatId(chatId.toString());
            photo.setPhoto(new InputFile(photoFileIds.get(0)));
            photo.setCaption(caption);
            photo.setReplyMarkup(KeyboardFactory.getSwipeKeyboard(profileUser.getId()));
            bot.execute(photo);
        } else {
            // Несколько фотографий - отправить как группу
            SendMediaGroup mediaGroup = new SendMediaGroup();
            mediaGroup.setChatId(chatId.toString());
            
            List<InputMedia> media = new ArrayList<>();
            for (int i = 0; i < photoFileIds.size(); i++) {
                InputMediaPhoto photo = new InputMediaPhoto();
                photo.setMedia(photoFileIds.get(i));
                if (i == 0) {
                    photo.setCaption(caption);
                }
                media.add(photo);
            }
            
            mediaGroup.setMedias(media);
            bot.execute(mediaGroup);
            
            // Отправить кнопки отдельным сообщением
            SendMessage buttonMessage = new SendMessage();
            buttonMessage.setChatId(chatId.toString());
            buttonMessage.setText("Выберите действие:");
            buttonMessage.setReplyMarkup(KeyboardFactory.getSwipeKeyboard(profileUser.getId()));
            bot.execute(buttonMessage);
        }
    }

    /**
     * Форматировать описание профиля
     */
    private String formatProfileCaption(User user, Profile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("👤 ").append(user.getFirstName());
        
        if (user.getUsername() != null && !user.getUsername().isEmpty()) {
            sb.append(" (@").append(user.getUsername()).append(")");
        }
        
        if (user.getBirthDate() != null) {
            int age = java.time.LocalDate.now().getYear() - user.getBirthDate().getYear();
            sb.append("\n🎂 ").append(age).append(" лет");
        }
        
        if (user.getGender() != null) {
            String genderText = switch (user.getGender()) {
                case MALE -> "👨 Мужской";
                case FEMALE -> "👩 Женский";
                case OTHER -> "🌈 Другое";
                default -> "❓ Не указан";
            };
            sb.append("\n").append(genderText);
        }
        
        if (profile.getBio() != null && !profile.getBio().isEmpty()) {
            sb.append("\n📝 ").append(profile.getBio());
        }
        
        return sb.toString();
    }
}



