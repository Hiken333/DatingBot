package com.bestproduct.dating.service;

import com.bestproduct.dating.domain.entity.Notification;
import com.bestproduct.dating.domain.entity.User;
import com.bestproduct.dating.repository.NotificationRepository;
import com.bestproduct.dating.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис для управления уведомлениями (с асинхронной отправкой)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * Создать уведомление
     */
    @Transactional
    public Notification createNotification(Long userId, Notification.NotificationType type,
                                          String title, String message, Long referenceId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Notification notification = Notification.builder()
            .user(user)
            .type(type)
            .title(title)
            .message(message)
            .referenceId(referenceId)
            .isRead(false)
            .build();

        notification = notificationRepository.save(notification);
        log.debug("Notification created: userId={}, type={}", userId, type);
        
        return notification;
    }

    /**
     * Отправить уведомление о новом мэтче (асинхронно)
     */
    @Async("notificationExecutor")
    public void sendMatchNotification(Long userId, Long matchedUserId, Long matchId) {
        try {
            User matchedUser = userRepository.findById(matchedUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

            String title = "Новый мэтч! 🎉";
            String message = String.format("У вас мэтч с %s! Начните общение прямо сейчас.", 
                matchedUser.getFirstName());

            createNotification(userId, Notification.NotificationType.NEW_MATCH, 
                title, message, matchId);
        } catch (Exception e) {
            log.error("Error sending match notification to user {}", userId, e);
        }
    }

    /**
     * Отправить уведомление о новом лайке (асинхронно)
     */
    @Async("notificationExecutor")
    public void sendLikeNotification(Long userId, Long likedByUserId) {
        try {
            User likedByUser = userRepository.findById(likedByUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

            String title = "Кто-то вами интересуется 💙";
            String message = String.format("%s лайкнул(а) ваш профиль!", 
                likedByUser.getFirstName());

            createNotification(userId, Notification.NotificationType.NEW_LIKE, 
                title, message, likedByUserId);
        } catch (Exception e) {
            log.error("Error sending like notification to user {}", userId, e);
        }
    }

    /**
     * Отправить уведомление о супер лайке (асинхронно)
     */
    @Async("notificationExecutor")
    public void sendSuperLikeNotification(Long userId, Long superLikedByUserId) {
        try {
            User superLikedByUser = userRepository.findById(superLikedByUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

            String title = "Супер лайк! ⭐";
            String message = String.format("%s поставил(а) вам СУПЕР ЛАЙК!", 
                superLikedByUser.getFirstName());

            createNotification(userId, Notification.NotificationType.NEW_LIKE, 
                title, message, superLikedByUserId);
        } catch (Exception e) {
            log.error("Error sending super like notification to user {}", userId, e);
        }
    }

    /**
     * Отправить уведомление о новом сообщении
     */
    public void sendMessageNotification(Long userId, Long senderId, Long matchId) {
        User sender = userRepository.findById(senderId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String title = "Новое сообщение 💬";
        String message = String.format("%s отправил(а) вам сообщение", 
            sender.getFirstName());

        createNotification(userId, Notification.NotificationType.NEW_MESSAGE, 
            title, message, matchId);
    }

    /**
     * Отправить уведомление о приближающемся событии
     */
    public void sendEventStartingSoonNotification(Long userId, Long eventId, String eventTitle) {
        String title = "Событие скоро начнется! 🍻";
        String message = String.format("Событие \"%s\" начинается через час!", eventTitle);

        createNotification(userId, Notification.NotificationType.EVENT_STARTING_SOON, 
            title, message, eventId);
    }

    /**
     * Отправить уведомление о приглашении на событие
     */
    public void sendEventInvitationNotification(Long userId, Long eventId, String eventTitle) {
        String title = "Приглашение на событие 🎊";
        String message = String.format("Вас пригласили на событие \"%s\"!", eventTitle);

        createNotification(userId, Notification.NotificationType.EVENT_INVITATION, 
            title, message, eventId);
    }

    /**
     * Получить все уведомления пользователя
     */
    public List<Notification> getUserNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Получить непрочитанные уведомления
     */
    public List<Notification> getUnreadNotifications(Long userId) {
        return notificationRepository.findUnreadNotificationsByUserId(userId);
    }

    /**
     * Пометить уведомление как прочитанное
     */
    @Transactional
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        notification.markAsRead();
        notificationRepository.save(notification);
    }

    /**
     * Пометить все уведомления пользователя как прочитанные
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> notifications = notificationRepository.findUnreadNotificationsByUserId(userId);
        notifications.forEach(Notification::markAsRead);
        notificationRepository.saveAll(notifications);
    }

    /**
     * Получить количество непрочитанных уведомлений
     */
    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadNotificationsByUserId(userId);
    }
}



