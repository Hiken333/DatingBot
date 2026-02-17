package com.bestproduct.dating.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * История свайпов пользователя (лайк/дизлайк/скип)
 */
@Entity
@Table(name = "swipe_history", indexes = {
    @Index(name = "idx_swipe_from_user", columnList = "from_user_id"),
    @Index(name = "idx_swipe_to_user", columnList = "to_user_id"),
    @Index(name = "idx_swipe_created_at", columnList = "created_at"),
    @Index(name = "idx_swipe_type", columnList = "swipe_type")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_swipe_from_to", columnNames = {"from_user_id", "to_user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SwipeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "swipe_type", nullable = false)
    private SwipeType swipeType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum SwipeType {
        LIKE,        // Лайк
        DISLIKE,     // Дизлайк
        SUPER_LIKE   // Супер лайк
    }
}


