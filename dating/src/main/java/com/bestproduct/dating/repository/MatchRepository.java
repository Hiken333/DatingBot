package com.bestproduct.dating.repository;

import com.bestproduct.dating.domain.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    
    @Query("SELECT m FROM Match m WHERE (m.user1.id = :userId OR m.user2.id = :userId) AND m.status = :status")
    List<Match> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Match.MatchStatus status);
    
    @Query("SELECT m FROM Match m WHERE ((m.user1.id = :user1Id AND m.user2.id = :user2Id) OR " +
           "(m.user1.id = :user2Id AND m.user2.id = :user1Id))")
    Optional<Match> findByUsers(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);
    
    @Query("SELECT m FROM Match m WHERE (m.user1.id = :userId OR m.user2.id = :userId) " +
           "AND m.status = 'ACTIVE' ORDER BY m.createdAt DESC")
    List<Match> findActiveMatchesByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT m FROM Match m WHERE (m.user1.id = :userId OR m.user2.id = :userId) " +
           "AND m.status = 'ACTIVE' ORDER BY m.createdAt DESC",
           countQuery = "SELECT COUNT(m) FROM Match m WHERE (m.user1.id = :userId OR m.user2.id = :userId) " +
           "AND m.status = 'ACTIVE'")
    org.springframework.data.domain.Page<Match> findActiveMatchesByUserIdWithPagination(@Param("userId") Long userId, 
                                                        org.springframework.data.domain.Pageable pageable);
    
    boolean existsByUser1IdAndUser2Id(Long user1Id, Long user2Id);
    
    @Query("SELECT COUNT(m) FROM Match m WHERE (m.user1.id = :userId OR m.user2.id = :userId) AND m.status = 'ACTIVE'")
    long countActiveMatchesByUserId(@Param("userId") Long userId);
}



