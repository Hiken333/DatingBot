package com.bestproduct.dating.repository;

import com.bestproduct.dating.domain.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    
    List<Review> findByReviewedUserId(Long reviewedUserId);
    
    List<Review> findByReviewerId(Long reviewerId);
    
    Optional<Review> findByReviewerIdAndReviewedUserId(Long reviewerId, Long reviewedUserId);
    
    boolean existsByReviewerIdAndReviewedUserId(Long reviewerId, Long reviewedUserId);
    
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.reviewedUser.id = :userId")
    Double calculateAverageRating(@Param("userId") Long userId);
    
    @Query("SELECT COUNT(r) FROM Review r WHERE r.reviewedUser.id = :userId")
    long countReviewsForUser(@Param("userId") Long userId);
}



