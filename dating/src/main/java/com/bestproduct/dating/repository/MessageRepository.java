package com.bestproduct.dating.repository;

import com.bestproduct.dating.domain.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    
    List<Message> findByMatchIdOrderByCreatedAtAsc(Long matchId);
    
    @Query("SELECT m FROM Message m WHERE m.match.id = :matchId " +
           "AND m.sender.id != :userId AND m.isRead = false")
    List<Message> findUnreadMessagesByMatch(@Param("matchId") Long matchId, @Param("userId") Long userId);
    
    @Query("SELECT COUNT(m) FROM Message m WHERE m.match.id IN " +
           "(SELECT ma.id FROM Match ma WHERE ma.user1.id = :userId OR ma.user2.id = :userId) " +
           "AND m.sender.id != :userId AND m.isRead = false")
    long countUnreadMessagesForUser(@Param("userId") Long userId);
    
    @Query("SELECT m FROM Message m WHERE m.match.id = :matchId ORDER BY m.createdAt DESC")
    List<Message> findRecentMessagesByMatch(@Param("matchId") Long matchId);
}



