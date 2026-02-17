package com.bestproduct.dating.repository;

import com.bestproduct.dating.domain.entity.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {

    /**
     * Найти лайк между двумя пользователями
     */
    Optional<Like> findByFromUserIdAndToUserId(Long fromUserId, Long toUserId);

    /**
     * Найти все лайки отправленные пользователем
     */
    List<Like> findByFromUserId(Long fromUserId);

    /**
     * Найти все лайки полученные пользователем
     */
    List<Like> findByToUserId(Long toUserId);

    /**
     * Найти лайки полученные пользователем после определенной даты
     */
    List<Like> findByToUserIdAndCreatedAtAfter(Long toUserId, LocalDateTime since);

    /**
     * Подсчитать количество лайков полученных пользователем после определенной даты
     */
    @Query("SELECT COUNT(l) FROM Like l WHERE l.toUser.id = :toUserId AND l.createdAt >= :since")
    long countByToUserIdAndCreatedAtAfter(@Param("toUserId") Long toUserId, @Param("since") LocalDateTime since);

    /**
     * Подсчитать количество лайков отправленных пользователем после определенной даты
     */
    @Query("SELECT COUNT(l) FROM Like l WHERE l.fromUser.id = :fromUserId AND l.createdAt >= :since")
    long countLikesByUserSince(@Param("fromUserId") Long fromUserId, @Param("since") LocalDateTime since);

    /**
     * Проверить существование лайка между пользователями
     */
    boolean existsByFromUserIdAndToUserId(Long fromUserId, Long toUserId);

    /**
     * Найти пользователей, которые лайкнули текущего пользователя (взаимные лайки)
     */
    @Query("SELECT l.fromUser FROM Like l WHERE l.toUser.id = :toUserId AND l.createdAt >= :since")
    List<com.bestproduct.dating.domain.entity.User> findLikersByToUserIdAndCreatedAtAfter(
        @Param("toUserId") Long toUserId,
        @Param("since") LocalDateTime since
    );
}

