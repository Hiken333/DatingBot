package com.bestproduct.dating.repository;

import com.bestproduct.dating.domain.entity.SwipeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SwipeHistoryRepository extends JpaRepository<SwipeHistory, Long> {
    
    /**
     * Найти свайп между двумя пользователями
     */
    Optional<SwipeHistory> findByFromUserIdAndToUserId(Long fromUserId, Long toUserId);
    
    /**
     * Найти все свайпы пользователя после определенной даты
     */
    @Query("SELECT sh FROM SwipeHistory sh WHERE sh.fromUser.id = :fromUserId AND sh.createdAt >= :since")
    List<SwipeHistory> findByFromUserIdAndCreatedAtAfter(@Param("fromUserId") Long fromUserId, @Param("since") LocalDateTime since);
    
    /**
     * Получить ID пользователей, которых не нужно показывать (уже оценены в определенный период)
     */
    @Query("SELECT sh.toUser.id FROM SwipeHistory sh WHERE sh.fromUser.id = :fromUserId AND " +
           "((sh.swipeType IN ('LIKE', 'SUPER_LIKE') AND sh.createdAt >= :oneDayAgo) OR " +
           " (sh.swipeType = 'DISLIKE' AND sh.createdAt >= :fourDaysAgo))")
    List<Long> findExcludedUserIds(@Param("fromUserId") Long fromUserId, 
                                   @Param("oneDayAgo") LocalDateTime oneDayAgo,
                                   @Param("fourDaysAgo") LocalDateTime fourDaysAgo);
}


