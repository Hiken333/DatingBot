package com.bestproduct.dating.service;

import com.bestproduct.dating.config.AppConfig;
import com.bestproduct.dating.domain.entity.Event;
import com.bestproduct.dating.domain.entity.EventRequest;
import com.bestproduct.dating.domain.entity.Profile;
import com.bestproduct.dating.domain.entity.User;
import com.bestproduct.dating.repository.EventRepository;
import com.bestproduct.dating.repository.EventRequestRepository;
import com.bestproduct.dating.repository.ProfileRepository;
import com.bestproduct.dating.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Сервис для управления событиями (пьянками)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final EventRequestRepository eventRequestRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final NotificationService notificationService;
    private final TelegramNotificationService telegramNotificationService;
    private final AppConfig appConfig;

    /**
     * Создать новое событие
     */
    @Transactional
    // @CacheEvict(value = "events", allEntries = true)
    public Event createEvent(Long organizerId, String title, String description,
                            Point location, String locationName, String city,
                            LocalDateTime eventDate, int maxParticipants,
                            Set<Profile.AlcoholPreference> alcoholTypes,
                            Event.EventType eventType) {
        User organizer = userRepository.findById(organizerId)
            .orElseThrow(() -> new IllegalArgumentException("Organizer not found"));

        // Проверка лимита активных событий
        long activeEvents = eventRepository.countActiveEventsByOrganizer(organizerId);
        if (activeEvents >= appConfig.getEvents().getMaxActiveEventsPerUser()) {
            throw new IllegalArgumentException("Maximum active events limit reached");
        }

        // Валидация описания
        if (description.length() < appConfig.getEvents().getMinDescriptionLength() ||
            description.length() > appConfig.getEvents().getMaxDescriptionLength()) {
            throw new IllegalArgumentException("Description length is invalid");
        }

        Event event = Event.builder()
            .organizer(organizer)
            .title(title)
            .description(description)
            .location(location)
            .locationName(locationName)
            .city(city)
            .eventDate(eventDate)
            .maxParticipants(maxParticipants)
            .alcoholTypes(alcoholTypes)
            .eventType(eventType)
            .status(Event.EventStatus.UPCOMING)
            .isPublic(true)
            .build();

        // Организатор автоматически становится участником
        event.addParticipant(organizer);

        event = eventRepository.save(event);
        log.info("Event created: id={}, organizerId={}", event.getId(), organizerId);
        
        return event;
    }

    /**
     * Найти события поблизости
     */
    // @Cacheable(value = "events", key = "'nearby_' + #userId + '_' + #radiusKm")
    @Transactional(readOnly = true)
    public List<Event> findNearbyEvents(Long userId, int radiusKm, int limit) {
        Profile userProfile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        if (userProfile.getLocation() == null) {
            throw new IllegalArgumentException("User location not set");
        }

        int maxRadius = appConfig.getGeo().getMaxSearchRadiusKm();
        if (radiusKm > maxRadius) {
            radiusKm = maxRadius;
        }

        double radiusMeters = radiusKm * 1000.0;

        List<Event> events = eventRepository.findNearbyUpcomingEvents(
            userProfile.getLocation(),
            radiusMeters,
            LocalDateTime.now(),
            limit
        );

        // Ensure organizer and participants are loaded to avoid LazyInitializationException
        events.forEach(event -> {
            if (event.getOrganizer() != null) {
                // Touch the organizer to ensure it's loaded
                event.getOrganizer().getFirstName();
            }
            // Touch the participants collection to ensure it's loaded
            event.getParticipants().size();
        });

        return events;
    }

    /**
     * Подать заявку на участие в событии
     */
    @Transactional
    public EventRequest requestToJoinEvent(Long eventId, Long userId, String message) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Проверка доступных мест
        if (!event.hasAvailableSlots()) {
            throw new IllegalArgumentException("Event is full");
        }

        // Проверка существующей заявки
        if (eventRequestRepository.findByEventIdAndUserId(eventId, userId).isPresent()) {
            throw new IllegalArgumentException("Request already exists");
        }

        // Проверка, не является ли уже участником
        if (event.isParticipant(userId)) {
            throw new IllegalArgumentException("Already a participant");
        }

        EventRequest request = EventRequest.builder()
            .event(event)
            .user(user)
            .message(message)
            .status(EventRequest.RequestStatus.PENDING)
            .build();

        request = eventRequestRepository.save(request);
        
        // Уведомление организатору
        notificationService.createNotification(
            event.getOrganizer().getId(),
            com.bestproduct.dating.domain.entity.Notification.NotificationType.EVENT_INVITATION,
            "Новая заявка на событие",
            String.format("%s хочет присоединиться к вашему событию \"%s\"", 
                user.getFirstName(), event.getTitle()),
            eventId
        );
        
        // Отправка уведомления в Telegram организатору о новой заявке
        try {
            telegramNotificationService.sendNewEventRequestNotification(
                event.getOrganizer().getId(), 
                eventId, 
                event.getTitle(),
                user.getFirstName(),
                message
            );
        } catch (Exception e) {
            log.error("Failed to send Telegram notification to organizer about new request", e);
        }

        log.info("Event join request created: eventId={}, userId={}", eventId, userId);
        return request;
    }

    /**
     * Одобрить заявку на участие
     */
    @Transactional
    public void approveRequest(Long requestId, Long organizerId) {
        EventRequest request = eventRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        Event event = request.getEvent();
        
        // Проверка прав организатора
        if (!event.isOrganizer(organizerId)) {
            throw new IllegalArgumentException("Only organizer can approve requests");
        }

        // Проверка доступных мест
        if (!event.hasAvailableSlots()) {
            throw new IllegalArgumentException("Event is full");
        }

        request.setStatus(EventRequest.RequestStatus.APPROVED);
        request.setReviewedById(organizerId);
        request.setReviewedAt(LocalDateTime.now());
        eventRequestRepository.save(request);

        // Добавление участника
        event.addParticipant(request.getUser());
        eventRepository.save(event);

        // Уведомление пользователю
        notificationService.createNotification(
            request.getUser().getId(),
            com.bestproduct.dating.domain.entity.Notification.NotificationType.EVENT_APPROVED,
            "Заявка одобрена! 🎉",
            String.format("Ваша заявка на событие \"%s\" была одобрена!", event.getTitle()),
            event.getId()
        );
        
        // Отправка уведомления в Telegram
        try {
            telegramNotificationService.sendEventApprovedNotification(
                request.getUser().getId(), 
                event.getId(), 
                event.getTitle()
            );
        } catch (Exception e) {
            log.error("Failed to send Telegram notification for approved event request", e);
        }

        // Уведомление организатору о том, что заявка одобрена
        try {
            telegramNotificationService.sendEventRequestApprovedToOrganizerNotification(
                organizerId, 
                event.getId(), 
                event.getTitle(),
                request.getUser().getFirstName(),
                event.getParticipants().size()
            );
        } catch (Exception e) {
            log.error("Failed to send Telegram notification to organizer about approved request", e);
        }

        log.info("Event request approved: requestId={}, eventId={}", requestId, event.getId());
    }

    /**
     * Отклонить заявку на участие
     */
    @Transactional
    public void rejectRequest(Long requestId, Long organizerId) {
        EventRequest request = eventRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        Event event = request.getEvent();
        
        // Проверка прав организатора
        if (!event.isOrganizer(organizerId)) {
            throw new IllegalArgumentException("Only organizer can reject requests");
        }

        request.setStatus(EventRequest.RequestStatus.REJECTED);
        request.setReviewedById(organizerId);
        request.setReviewedAt(LocalDateTime.now());
        eventRequestRepository.save(request);

        // Уведомление пользователю об отклонении заявки
        notificationService.createNotification(
            request.getUser().getId(),
            com.bestproduct.dating.domain.entity.Notification.NotificationType.EVENT_REJECTED,
            "Заявка отклонена",
            String.format("К сожалению, ваша заявка на событие \"%s\" была отклонена", event.getTitle()),
            event.getId()
        );
        
        // Отправка уведомления в Telegram
        try {
            telegramNotificationService.sendEventRejectedNotification(
                request.getUser().getId(), 
                event.getId(), 
                event.getTitle()
            );
        } catch (Exception e) {
            log.error("Failed to send Telegram notification for rejected event request", e);
        }

        // Уведомление организатору о том, что заявка отклонена
        try {
            telegramNotificationService.sendEventRequestRejectedToOrganizerNotification(
                organizerId, 
                event.getId(), 
                event.getTitle(),
                request.getUser().getFirstName()
            );
        } catch (Exception e) {
            log.error("Failed to send Telegram notification to organizer about rejected request", e);
        }

        log.info("Event request rejected: requestId={}, eventId={}", requestId, event.getId());
    }

    /**
     * Покинуть событие
     */
    @Transactional
    // @CacheEvict(value = "events", allEntries = true)
    public void leaveEvent(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Организатор не может покинуть событие
        if (event.isOrganizer(userId)) {
            throw new IllegalArgumentException("Organizer cannot leave event. Cancel it instead.");
        }

        event.removeParticipant(user);
        eventRepository.save(event);
        
        // Уведомление организатору о том, что участник покинул событие
        try {
            telegramNotificationService.sendParticipantLeftEventNotification(
                event.getOrganizer().getId(), 
                event.getId(), 
                event.getTitle(),
                user.getFirstName()
            );
        } catch (Exception e) {
            log.error("Failed to send Telegram notification to organizer about participant leaving", e);
        }
        
        log.info("User left event: eventId={}, userId={}", eventId, userId);
    }

    /**
     * Отменить событие
     */
    @Transactional
    // @CacheEvict(value = "events", allEntries = true)
    public void cancelEvent(Long eventId, Long organizerId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        if (!event.isOrganizer(organizerId)) {
            throw new IllegalArgumentException("Only organizer can cancel event");
        }

        event.setStatus(Event.EventStatus.CANCELLED);
        eventRepository.save(event);

        // Уведомление всем участникам
        event.getParticipants().forEach(participant -> {
            if (!participant.getId().equals(organizerId)) {
                notificationService.createNotification(
                    participant.getId(),
                    com.bestproduct.dating.domain.entity.Notification.NotificationType.SYSTEM_NOTIFICATION,
                    "Событие отменено",
                    String.format("Событие \"%s\" было отменено организатором", event.getTitle()),
                    eventId
                );
                
                // Отправка уведомления в Telegram
                try {
                    telegramNotificationService.sendEventCancelledNotification(
                        participant.getId(), 
                        eventId, 
                        event.getTitle()
                    );
                } catch (Exception e) {
                    log.error("Failed to send Telegram notification for cancelled event to user {}", participant.getId(), e);
                }
            }
        });

        log.info("Event cancelled: eventId={}", eventId);
    }

    /**
     * Получить события пользователя
     */
    @Transactional(readOnly = true)
    public List<Event> getUserEvents(Long userId) {
        return eventRepository.findEventsByParticipant(userId);
    }

    /**
     * Получить события, созданные пользователем
     */
    @Transactional(readOnly = true)
    public List<Event> getOrganizedEvents(Long userId) {
        return eventRepository.findByOrganizerId(userId);
    }

    /**
     * Удалить участника из события (только для организатора)
     */
    @Transactional
    public void removeParticipantFromEvent(Long eventId, Long userIdToRemove, Long organizerId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        // Проверка прав организатора
        if (!event.isOrganizer(organizerId)) {
            throw new IllegalArgumentException("Only organizer can remove participants");
        }

        // Организатор не может удалить сам себя
        if (userIdToRemove.equals(organizerId)) {
            throw new IllegalArgumentException("Organizer cannot remove themselves from event");
        }

        User userToRemove = userRepository.findById(userIdToRemove)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Проверка, что пользователь является участником
        if (!event.isParticipant(userIdToRemove)) {
            throw new IllegalArgumentException("User is not a participant of this event");
        }

        event.removeParticipant(userToRemove);
        eventRepository.save(event);

        // Уведомление удаленному пользователю
        notificationService.createNotification(
            userIdToRemove,
            com.bestproduct.dating.domain.entity.Notification.NotificationType.SYSTEM_NOTIFICATION,
            "Исключение из события",
            String.format("Вы были исключены из события \"%s\"", event.getTitle()),
            eventId
        );
        
        // Отправка уведомления в Telegram исключенному пользователю
        try {
            telegramNotificationService.sendParticipantKickedNotification(
                userIdToRemove, 
                eventId, 
                event.getTitle()
            );
        } catch (Exception e) {
            log.error("Failed to send Telegram notification to kicked participant", e);
        }

        log.info("User removed from event: eventId={}, userId={}", eventId, userIdToRemove);
    }

    /**
     * Получить заявки на участие в событии (только для организатора)
     */
    @Transactional(readOnly = true)
    public List<EventRequest> getEventRequests(Long eventId, Long organizerId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        // Проверка прав организатора
        if (!event.isOrganizer(organizerId)) {
            throw new IllegalArgumentException("Only organizer can view event requests");
        }

        List<EventRequest> requests = eventRequestRepository.findByEventIdAndStatus(eventId, EventRequest.RequestStatus.PENDING);
        
        // Eagerly load User entities to prevent LazyInitializationException
        requests.forEach(request -> {
            if (request.getUser() != null) {
                // Touch the user to ensure it's loaded
                request.getUser().getFirstName();
                request.getUser().getUsername();
            }
        });
        
        return requests;
    }

    /**
     * Получить заявку по ID (только для организатора события)
     */
    @Transactional(readOnly = true)
    public EventRequest getEventRequest(Long requestId, Long organizerId) {
        EventRequest request = eventRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        // Проверка прав организатора
        if (!request.getEvent().isOrganizer(organizerId)) {
            throw new IllegalArgumentException("Only organizer can view this request");
        }

        return request;
    }

}



