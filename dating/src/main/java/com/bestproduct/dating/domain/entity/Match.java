package com.bestproduct.dating.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Сущность мэтча между двумя пользователями
 */
@Entity
@Table(name = "matches", indexes = {
    @Index(name = "idx_match_user1", columnList = "user1_id"),
    @Index(name = "idx_match_user2", columnList = "user2_id"),
    @Index(name = "idx_match_created", columnList = "created_at")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_match_users", columnNames = {"user1_id", "user2_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MatchStatus status = MatchStatus.ACTIVE;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "unmatched_by_user_id")
    private Long unmatchedByUserId;

    @Column(name = "unmatched_at")
    private LocalDateTime unmatchedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Message> messages = new HashSet<>();

    public enum MatchStatus {
        ACTIVE,
        UNMATCHED,
        REPORTED
    }

    /**
     * Проверка, является ли пользователь участником мэтча
     */
    public boolean isParticipant(Long userId) {
        return user1.getId().equals(userId) || user2.getId().equals(userId);
    }

    /**
     * Получить другого пользователя в мэтче
     */
    public User getOtherUser(Long userId) {
        if (user1.getId().equals(userId)) {
            return user2;
        } else if (user2.getId().equals(userId)) {
            return user1;
        }
        throw new IllegalArgumentException("User is not part of this match");
    }
}



