package com.bestproduct.dating.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Основная сущность пользователя системы
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_telegram_id", columnList = "telegram_id", unique = true),
    @Index(name = "idx_username", columnList = "username"),
    @Index(name = "idx_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Column(name = "is_banned", nullable = false)
    @Builder.Default
    private Boolean isBanned = false;

    @Column(name = "ban_reason", length = 500)
    private String banReason;

    @Column(name = "report_count", nullable = false)
    @Builder.Default
    private Integer reportCount = 0;

    @Column(name = "last_active")
    private LocalDateTime lastActive;

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

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"user", "alcoholPreferences", "interests", "photoUrls"})
    private Profile profile;

    @OneToMany(mappedBy = "fromUser", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnoreProperties({"fromUser", "toUser"})
    private Set<Like> sentLikes = new HashSet<>();

    @OneToMany(mappedBy = "toUser", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnoreProperties({"fromUser", "toUser"})
    private Set<Like> receivedLikes = new HashSet<>();

    @OneToMany(mappedBy = "user1", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnoreProperties({"user1", "user2"})
    private Set<Match> matchesAsUser1 = new HashSet<>();

    @OneToMany(mappedBy = "user2", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnoreProperties({"user1", "user2"})
    private Set<Match> matchesAsUser2 = new HashSet<>();

    @OneToMany(mappedBy = "reporter", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnoreProperties({"reporter", "reported"})
    private Set<Report> reportsMade = new HashSet<>();

    @OneToMany(mappedBy = "reported", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnoreProperties({"reporter", "reported"})
    private Set<Report> reportsReceived = new HashSet<>();

    @ManyToMany(mappedBy = "participants", fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnoreProperties({"organizer", "participants", "requests"})
    private Set<Event> participatingEvents = new HashSet<>();

    public enum Gender {
        MALE, FEMALE, OTHER
    }

    public enum UserStatus {
        PENDING_VERIFICATION,
        ACTIVE,
        SUSPENDED,
        BANNED
    }

    public enum UserRole {
        USER,
        MODERATOR,
        ADMIN
    }

    /**
     * Проверка, что пользователь достиг минимального возраста
     */
    public boolean isOfLegalAge(int minAge) {
        return birthDate.plusYears(minAge).isBefore(LocalDate.now()) 
            || birthDate.plusYears(minAge).isEqual(LocalDate.now());
    }

    /**
     * Получить возраст пользователя
     */
    public int getAge() {
        return LocalDate.now().getYear() - birthDate.getYear();
    }

    /**
     * Проверка, активен ли пользователь
     */
    public boolean isActive() {
        return UserStatus.ACTIVE.equals(status) && !isBanned;
    }
}



