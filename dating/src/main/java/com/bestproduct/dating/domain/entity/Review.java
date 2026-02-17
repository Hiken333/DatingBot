package com.bestproduct.dating.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Сущность отзыва о пользователе
 */
@Entity
@Table(name = "reviews", indexes = {
    @Index(name = "idx_review_reviewer", columnList = "reviewer_id"),
    @Index(name = "idx_review_reviewed", columnList = "reviewed_user_id"),
    @Index(name = "idx_review_created", columnList = "created_at")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_review_users", columnNames = {"reviewer_id", "reviewed_user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_user_id", nullable = false)
    private User reviewedUser;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "was_meeting", nullable = false)
    @Builder.Default
    private Boolean wasMeeting = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}



