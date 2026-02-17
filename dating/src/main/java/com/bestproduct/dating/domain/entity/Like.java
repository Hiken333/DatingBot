package com.bestproduct.dating.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Сущность лайка (свайпа вправо) между пользователями
 */
@Entity
@Table(name = "likes", indexes = {
    @Index(name = "idx_from_user", columnList = "from_user_id"),
    @Index(name = "idx_to_user", columnList = "to_user_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_like_from_to", columnNames = {"from_user_id", "to_user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "is_super_like", nullable = false)
    @Builder.Default
    private Boolean isSuperLike = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}



