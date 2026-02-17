package com.bestproduct.dating.telegram.handler;

import com.bestproduct.dating.domain.entity.Match;
import com.bestproduct.dating.domain.entity.User;
import com.bestproduct.dating.service.MatchingService;
import com.bestproduct.dating.service.UserService;
import com.bestproduct.dating.telegram.DatingBot;
import com.bestproduct.dating.telegram.keyboard.KeyboardFactory;
import com.bestproduct.dating.telegram.util.LocationValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class MatchesCommandHandler implements CommandHandler {

    private final UserService userService;
    private final MatchingService matchingService;
    private final LocationValidator locationValidator;

    @Override
    public void handle(DatingBot bot, Update update) {
        handleAsync(bot, update); // Запускаем асинхронно
    }

    /**
     * Асинхронная обработка команды матчей
     */
    @Async("telegramBotExecutor")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void handleAsync(DatingBot bot, Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

        // Проверить геолокацию и фото
        if (!locationValidator.checkLocationAndPhotosAndSendMessage(bot, chatId, telegramId, 
            "💬 Для просмотра мэтчей необходимо указать ваше местоположение и загрузить фото профиля.")) {
            return;
        }

        try {
            Optional<User> userOpt = userService.findByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                sendNotRegisteredMessage(bot, chatId);
                return;
            }

            User user = userOpt.get();
            List<Match> matches = matchingService.getActiveMatches(user.getId(), 0, 20);

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());

            if (matches.isEmpty()) {
                message.setText(
                    "💬 Ваши мэтчи\n\n" +
                    "У вас пока нет мэтчей.\n\n" +
                    "🔍 Начните свайпать профили, чтобы найти собутыльников!\n" +
                    "Используйте кнопку '🔍 Искать' в меню.");
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("💬 Ваши мэтчи (").append(matches.size()).append(")\n\n");
                
                List<Long> matchIds = new ArrayList<>();
                for (Match match : matches) {
                    User otherUser = match.getOtherUser(user.getId());
                    sb.append("👤 ").append(otherUser.getFirstName());
                    if (otherUser.getLastName() != null) {
                        sb.append(" ").append(otherUser.getLastName());
                    }
                    sb.append(", ").append(otherUser.getAge()).append(" лет\n");
                    sb.append("   Мэтч: ").append(match.getCreatedAt().toLocalDate()).append("\n\n");
                    matchIds.add(match.getId());
                }
                
                sb.append("💡 Нажмите кнопку ниже, чтобы открыть чат с мэтчем.");
                message.setText(sb.toString());
                
                // Добавить кнопки для открытия чатов
                if (!matchIds.isEmpty()) {
                    message.setReplyMarkup(KeyboardFactory.getMatchesKeyboard(matchIds));
                }
            }

            try {
                bot.execute(message);
            } catch (TelegramApiException e) {
                log.error("Error sending matches message", e);
            }
            
        } catch (Exception e) {
            log.error("Error in matches command", e);
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



