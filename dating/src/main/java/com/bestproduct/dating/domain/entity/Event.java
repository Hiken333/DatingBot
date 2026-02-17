package com.bestproduct.dating.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Сущность события (пьянки)
 */
@Entity
@Table(name = "events", indexes = {
    @Index(name = "idx_event_organizer", columnList = "organizer_id"),
    @Index(name = "idx_event_location", columnList = "location"),
    @Index(name = "idx_event_date", columnList = "event_date"),
    @Index(name = "idx_event_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false)
    @JsonIgnoreProperties({"profile", "events", "participatingEvents", "likes", "matches", "messages", "notifications", "reports", "reviews"})
    private User organizer;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "location", nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(name = "location_name", length = 200)
    private String locationName;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "event_date", nullable = false)
    private LocalDateTime eventDate;

    @Column(name = "max_participants", nullable = false)
    @Builder.Default
    private Integer maxParticipants = 10;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_alcohol_types", joinColumns = @JoinColumn(name = "event_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "alcohol_type")
    @Builder.Default
    private Set<Profile.AlcoholPreference> alcoholTypes = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private EventStatus status = EventStatus.UPCOMING;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = true;

    @Column(name = "age_restriction")
    private Integer ageRestriction;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender_restriction")
    private GenderRestriction genderRestriction;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "event_participants",
        joinColumns = @JoinColumn(name = "event_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    @JsonIgnoreProperties({"profile", "events", "participatingEvents", "likes", "matches", "messages", "notifications", "reports", "reviews"})
    private Set<User> participants = new HashSet<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnoreProperties({"event", "user"})
    private Set<EventRequest> requests = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    public enum EventType {
        BAR_CRAWL,        // Поход по барам
        HOUSE_PARTY,      // Домашняя вечеринка
        CLUB_NIGHT,       // Клубная ночь
        WINE_TASTING,     // Дегустация вина
        BEER_GARDEN,      // Пивной сад
        COCKTAIL_BAR,     // Коктейльный бар
        CASUAL_DRINKS,    // Просто выпить
        OTHER
    }

    public enum EventStatus {
        UPCOMING,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }

    public enum GenderRestriction {
        MALE_ONLY,
        FEMALE_ONLY,
        NO_RESTRICTION
    }

    /**
     * Проверка, есть ли свободные места
     */
    public boolean hasAvailableSlots() {
        return participants.size() < maxParticipants;
    }

    /**
     * Проверка, является ли пользователь организатором
     */
    public boolean isOrganizer(Long userId) {
        return organizer.getId().equals(userId);
    }

    /**
     * Проверка, является ли пользователь участником
     */
    public boolean isParticipant(Long userId) {
        return participants.stream().anyMatch(p -> p.getId().equals(userId));
    }

    /**
     * Добавить участника
     */
    public boolean addParticipant(User user) {
        if (hasAvailableSlots()) {
            return participants.add(user);
        }
        return false;
    }

    /**
     * Удалить участника
     */
    public boolean removeParticipant(User user) {
        return participants.remove(user);
    }
}



