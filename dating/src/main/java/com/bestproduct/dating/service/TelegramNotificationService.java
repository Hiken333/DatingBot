package com.bestproduct.dating.service;

import com.bestproduct.dating.domain.entity.Notification;
import com.bestproduct.dating.domain.entity.User;
import com.bestproduct.dating.repository.NotificationRepository;
import com.bestproduct.dating.repository.UserRepository;
import com.bestproduct.dating.telegram.DatingBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

/**
 * Сервис для отправки уведомлений в Telegram
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramNotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ApplicationContext applicationContext;

    /**
     * Отправить уведомление о новом лайке в Telegram
     */
    @Async("notificationExecutor")
    public void sendLikeNotification(Long userId, Long likedByUserId) {
        try {
            User likedByUser = userRepository.findById(likedByUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

            String message = String.format("💙 %s лайкнул(а) ваш профиль!", 
                likedByUser.getFirstName());

            sendTelegramMessage(userId, message);
        } catch (Exception e) {
            log.error("Error sending like notification to user {}", userId, e);
        }
    }

    /**
     * Отправить уведомление о супер лайке в Telegram
     */
    @Async("notificationExecutor")
    public void sendSuperLikeNotification(Long userId, Long superLikedByUserId) {
        try {
            User superLikedByUser = userRepository.findById(superLikedByUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

            String message = String.format("⭐ %s поставил(а) вам СУПЕР ЛАЙК!", 
                superLikedByUser.getFirstName());

            sendTelegramMessage(userId, message);
        } catch (Exception e) {
            log.error("Error sending super like notification to user {}", userId, e);
        }
    }

    /**
     * Отправить уведомление о новом мэтче в Telegram
     */
    @Async("notificationExecutor")
    public void sendMatchNotification(Long userId, Long matchedUserId, Long matchId) {
        try {
            User matchedUser = userRepository.findById(matchedUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

            String message = String.format("🎉 У вас мэтч с %s! Начните общение прямо сейчас.", 
                matchedUser.getFirstName());

            sendTelegramMessage(userId, message);
        } catch (Exception e) {
            log.error("Error sending match notification to user {}", userId, e);
        }
    }

    /**
     * Отправить сообщение в Telegram
     */
    private void sendTelegramMessage(Long userId, String messageText) {
        try {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

            if (user.getTelegramId() == null) {
                log.warn("User {} has no telegram ID", userId);
                return;
            }

            SendMessage message = new SendMessage();
            message.setChatId(user.getTelegramId().toString());
            message.setText(messageText);

            // Получаем DatingBot из контекста, чтобы избежать циклической зависимости
            DatingBot DatingBot = applicationContext.getBean(DatingBot.class);
            DatingBot.execute(message);
            log.debug("Telegram notification sent to user {}", userId);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message to user {}", userId, e);
        } catch (Exception e) {
            log.error("Error sending Telegram message to user {}", userId, e);
        }
    }

    /**
     * Отправить уведомление об одобрении заявки на событие
     */
    @Async
    public void sendEventApprovedNotification(Long userId, Long eventId, String eventTitle) {
        try {
            String message = String.format("🎉 Ваша заявка на событие \"%s\" была одобрена!\n\n" +
                "Теперь вы участник события! Увидимся на мероприятии! 🍻", eventTitle);

            sendTelegramMessage(userId, message);
        } catch (Exception e) {
            log.error("Error sending event approved notification to user {}", userId, e);
        }
    }

    /**
     * Отправить уведомление об отклонении заявки на событие
     */
    @Async
    public void sendEventRejectedNotification(Long userId, Long eventId, String eventTitle) {
        try {
            String message = String.format("😔 К сожалению, ваша заявка на событие \"%s\" была отклонена.\n\n" +
                "Не расстраивайтесь! Попробуйте найти другие интересные события! 🍻", eventTitle);

            sendTelegramMessage(userId, message);
        } catch (Exception e) {
            log.error("Error sending event rejected notification to user {}", userId, e);
        }
    }

    /**
     * Отправить уведомление об отмене события
     */
    @Async
    public void sendEventCancelledNotification(Long userId, Long eventId, String eventTitle) {
        try {
            String message = String.format("❌ Событие \"%s\" было отменено организатором.\n\n" +
                "К сожалению, мероприятие не состоится. Ищите другие интересные события! 🍻", eventTitle);

            sendTelegramMessage(userId, message);
        } catch (Exception e) {
            log.error("Error sending event cancelled notification to user {}", userId, e);
        }
    }

    /**
     * Отправить уведомление организатору об одобрении заявки
     */
    @Async
    public void sendEventRequestApprovedToOrganizerNotification(Long organizerId, Long eventId, String eventTitle, String participantName, int participantCount) {
        try {
            String message = String.format("✅ Заявка на событие \"%s\" одобрена!\n\n" +
                "👤 %s добавлен(а) к участникам события.\n" +
                "Теперь у вас %d участников.", eventTitle, participantName, participantCount);

            sendTelegramMessage(organizerId, message);
        } catch (Exception e) {
            log.error("Error sending event request approved notification to organizer {}", organizerId, e);
        }
    }

    /**
     * Отправить уведомление организатору об отклонении заявки
     */
    @Async
    public void sendEventRequestRejectedToOrganizerNotification(Long organizerId, Long eventId, String eventTitle, String participantName) {
        try {
            String message = String.format("❌ Заявка на событие \"%s\" отклонена.\n\n" +
                "👤 %s не будет участвовать в событии.", eventTitle, participantName);

            sendTelegramMessage(organizerId, message);
        } catch (Exception e) {
            log.error("Error sending event request rejected notification to organizer {}", organizerId, e);
        }
    }

    /**
     * Отправить уведомление организатору о том, что участник покинул событие
     */
    @Async
    public void sendParticipantLeftEventNotification(Long organizerId, Long eventId, String eventTitle, String participantName) {
        try {
            String message = String.format("👋 Участник покинул событие \"%s\"\n\n" +
                "👤 %s больше не участвует в мероприятии.", eventTitle, participantName);

            sendTelegramMessage(organizerId, message);
        } catch (Exception e) {
            log.error("Error sending participant left event notification to organizer {}", organizerId, e);
        }
    }

    /**
     * Отправить уведомление пользователю об исключении из события
     */
    @Async
    public void sendParticipantKickedNotification(Long userId, Long eventId, String eventTitle) {
        try {
            String message = String.format("🚫 Вы были исключены из события \"%s\"\n\n" +
                "Организатор исключил вас из мероприятия. Если это ошибка, свяжитесь с организатором.", eventTitle);

            sendTelegramMessage(userId, message);
        } catch (Exception e) {
            log.error("Error sending participant kicked notification to user {}", userId, e);
        }
    }

    /**
     * Отправить уведомление организатору о новой заявке на участие
     */
    @Async
    public void sendNewEventRequestNotification(Long organizerId, Long eventId, String eventTitle, String requesterName, String requestMessage) {
        try {
            String message = String.format("📝 Новая заявка на событие \"%s\"\n\n" +
                "👤 %s хочет присоединиться к вашему событию", eventTitle, requesterName);
            
            if (requestMessage != null && !requestMessage.trim().isEmpty()) {
                message += String.format("\n💬 Сообщение: \"%s\"", requestMessage);
            }
            
            message += "\n\nПерейдите в управление событием, чтобы рассмотреть заявку.";

            sendTelegramMessage(organizerId, message);
        } catch (Exception e) {
            log.error("Error sending new event request notification to organizer {}", organizerId, e);
        }
    }

    /**
     * Отправить все непрочитанные уведомления пользователю
     */
    @Transactional
    public void sendUnreadNotifications(Long userId) {
        try {
            List<Notification> unreadNotifications = notificationRepository
                .findUnreadNotificationsByUserId(userId);

            if (unreadNotifications.isEmpty()) {
                return;
            }

            StringBuilder messageText = new StringBuilder("📬 У вас " + unreadNotifications.size() + " новых уведомлений:\n\n");
            
            for (Notification notification : unreadNotifications) {
                messageText.append("• ").append(notification.getTitle()).append("\n");
                messageText.append("  ").append(notification.getMessage()).append("\n\n");
                
                // Пометить как прочитанное
                notification.markAsRead();
            }

            notificationRepository.saveAll(unreadNotifications);
            sendTelegramMessage(userId, messageText.toString());
        } catch (Exception e) {
            log.error("Error sending unread notifications to user {}", userId, e);
        }
    }
}


