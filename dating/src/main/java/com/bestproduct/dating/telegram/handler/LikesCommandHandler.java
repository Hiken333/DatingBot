package com.bestproduct.dating.telegram.handler;

import com.bestproduct.dating.domain.entity.User;
import com.bestproduct.dating.repository.LikeRepository;
import com.bestproduct.dating.service.UserService;
import com.bestproduct.dating.telegram.DatingBot;
import com.bestproduct.dating.telegram.util.LocationValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class LikesCommandHandler implements CommandHandler {

    private final UserService userService;
    private final LikeRepository likeRepository;
    private final LocationValidator locationValidator;

    @Override
    public void handle(DatingBot bot, Update update) {
        handleAsync(bot, update); // Запускаем асинхронно
    }

    /**
     * Асинхронная обработка команды лайков
     */
    @Async("telegramBotExecutor")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void handleAsync(DatingBot bot, Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

        // Проверить геолокацию и фото
        if (!locationValidator.checkLocationAndPhotosAndSendMessage(bot, chatId, telegramId, 
            "❤️ Для просмотра лайков необходимо указать ваше местоположение и загрузить фото профиля.")) {
            return;
        }

        try {
            Optional<User> userOpt = userService.findByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                sendNotRegisteredMessage(bot, chatId);
                return;
            }

            User user = userOpt.get();

            // Получить лайки за последние 7 дней
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            List<User> likers = likeRepository.findLikersByToUserIdAndCreatedAtAfter(user.getId(), sevenDaysAgo);

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());

            if (likers.isEmpty()) {
                message.setText("❤️ Лайки\n\n" +
                    "За последнюю неделю вас никто не лайкал.\n\n" +
                    "💡 Чтобы получать лайки:\n" +
                    "• Заполните профиль\n" +
                    "• Добавьте фото\n" +
                    "• Будьте активны в поиске");
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("❤️ Лайки (").append(likers.size()).append(" за неделю)\n\n");

                for (User liker : likers) {
                    sb.append("👤 ").append(liker.getFirstName());
                    if (liker.getLastName() != null) {
                        sb.append(" ").append(liker.getLastName());
                    }
                    sb.append(", ").append(liker.getAge()).append(" лет\n");
                    if (liker.getLastActive() != null) {
                        sb.append("   Лайк: ").append(liker.getLastActive().toLocalDate()).append("\n\n");
                    } else {
                        sb.append("   Лайк: недавно\n\n");
                    }
                }

                sb.append("💕 Эти люди вас лайкнули!\n");
                sb.append("Используйте 🔍 Искать чтобы найти их снова.");

                message.setText(sb.toString());
            }

            try {
                bot.execute(message);
            } catch (TelegramApiException e) {
                log.error("Error sending likes message", e);
            }

        } catch (Exception e) {
            log.error("Error in likes command", e);
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


