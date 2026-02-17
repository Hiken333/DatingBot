package com.bestproduct.dating.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Профиль пользователя с детальной информацией для знакомств
 */
@Entity
@Table(name = "profiles", indexes = {
    @Index(name = "idx_profile_location", columnList = "location"),
    @Index(name = "idx_profile_visible", columnList = "is_visible")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @JsonIgnoreProperties({"profile", "events", "participatingEvents", "likes", "matches", "messages", "notifications", "reports", "reviews"})
    private User user;

    @Column(name = "bio", length = 1000)
    private String bio;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_photos", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "photo_url", length = 500)
    @Builder.Default
    private List<String> photoUrls = new ArrayList<>();

    @Column(name = "location", columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "location_updated_at")
    private LocalDateTime locationUpdatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_alcohol_preferences", joinColumns = @JoinColumn(name = "profile_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "preference")
    @Builder.Default
    private List<AlcoholPreference> alcoholPreferences = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "drinking_frequency")
    private DrinkingFrequency drinkingFrequency;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_interests", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "interest", length = 50)
    @Builder.Default
    private List<String> interests = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "looking_for")
    private LookingFor lookingFor;

    @Column(name = "min_age_preference")
    private Integer minAgePreference;

    @Column(name = "max_age_preference")
    private Integer maxAgePreference;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender_preference")
    private GenderPreference genderPreference;

    @Column(name = "max_distance_km")
    @Builder.Default
    private Integer maxDistanceKm = 10;

    @Column(name = "is_visible", nullable = false)
    @Builder.Default
    private Boolean isVisible = true;

    @Column(name = "show_distance", nullable = false)
    @Builder.Default
    private Boolean showDistance = true;

    @Column(name = "show_age", nullable = false)
    @Builder.Default
    private Boolean showAge = true;

    @Column(name = "profile_completed", nullable = false)
    @Builder.Default
    private Boolean profileCompleted = false;

    @Column(name = "rating", nullable = false)
    @Builder.Default
    private Double rating = 5.0;

    @Column(name = "rating_count", nullable = false)
    @Builder.Default
    private Integer ratingCount = 0;

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

    public enum AlcoholPreference {
        BEER, WINE, WHISKEY, VODKA, COCKTAILS, CHAMPAGNE, 
        RUM, TEQUILA, GIN, SAKE, OTHER
    }

    public enum DrinkingFrequency {
        RARELY,           // Редко
        SOCIALLY,         // В компании
        REGULARLY,        // Регулярно
        DAILY             // Ежедневно
    }

    public enum LookingFor {
        DRINKING_BUDDY,   // Собутыльник
        PARTY_FRIEND,     // Друг для вечеринок
        RELATIONSHIP,     // Отношения
        JUST_FUN,         // Просто повеселиться
        ANYTHING          // Что угодно
    }

    public enum GenderPreference {
        MALE, FEMALE, BOTH, OTHER
    }

    /**
     * Проверка, заполнен ли профиль полностью
     */
    public boolean isComplete() {
        return bio != null && !bio.isBlank()
            && photoUrls != null && !photoUrls.isEmpty()
            && location != null
            && alcoholPreferences != null && !alcoholPreferences.isEmpty()
            && drinkingFrequency != null
            && lookingFor != null;
    }

    /**
     * Обновить рейтинг профиля
     */
    public void updateRating(double newRating) {
        if (ratingCount == 0) {
            this.rating = newRating;
        } else {
            this.rating = ((rating * ratingCount) + newRating) / (ratingCount + 1);
        }
        this.ratingCount++;
    }
}



