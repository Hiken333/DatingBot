package com.bestproduct.dating.repository;

import com.bestproduct.dating.domain.entity.Profile;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, Long> {
    
    Optional<Profile> findByUserId(Long userId);
    
    @Query(value = "SELECT p.* FROM profiles p " +
           "WHERE p.is_visible = true " +
           "AND p.user_id != :userId " +
           "AND p.location IS NOT NULL " +
           "AND EXISTS (" +
           "    SELECT 1 FROM profile_photos pp " +
           "    WHERE pp.profile_id = p.id" +
           ") " +
           "AND NOT EXISTS (" +
           "    SELECT 1 FROM swipe_history sh " +
           "    WHERE sh.from_user_id = :userId AND sh.to_user_id = p.user_id " +
           "    AND ((sh.swipe_type IN ('LIKE', 'SUPER_LIKE') AND sh.created_at >= CURRENT_TIMESTAMP - INTERVAL '4 days') " +
           "         OR (sh.swipe_type = 'DISLIKE' AND sh.created_at >= CURRENT_TIMESTAMP - INTERVAL '1 day'))" +
           ") " +
           "ORDER BY ST_Distance(p.location::geography, CAST(:userLocation AS geography)) ASC " +
           "LIMIT :limit",
           nativeQuery = true)
    List<Profile> findNearbyProfiles(
        @Param("userId") Long userId,
        @Param("userLocation") Point userLocation,
        @Param("limit") int limit
    );
    
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT p FROM Profile p WHERE p.id IN :profileIds")
    List<Profile> findByIdsWithUser(@Param("profileIds") List<Long> profileIds);
    
    @Query("SELECT p FROM Profile p WHERE p.isVisible = true AND p.profileCompleted = true AND SIZE(p.photoUrls) > 0")
    List<Profile> findVisibleAndCompleteProfiles();
    
    @Query("SELECT p FROM Profile p JOIN p.user u WHERE u.id = :userId")
    Optional<Profile> findByUserIdWithUser(@Param("userId") Long userId);
}



