package com.bestproduct.dating.repository;

import com.bestproduct.dating.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByTelegramId(Long telegramId);
    
    boolean existsByTelegramId(Long telegramId);
    
    List<User> findByStatus(User.UserStatus status);
    
    List<User> findByIsBannedTrue();
    
    @Query("SELECT u FROM User u WHERE u.reportCount >= :threshold AND u.isBanned = false")
    List<User> findUsersWithHighReportCount(@Param("threshold") int threshold);
    
    @Query("SELECT u FROM User u WHERE u.lastActive < :since")
    List<User> findInactiveUsersSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(u) FROM User u WHERE u.status = :status")
    long countByStatus(@Param("status") User.UserStatus status);
}



