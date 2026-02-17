package com.bestproduct.dating.repository;

import com.bestproduct.dating.domain.entity.Event;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    
    List<Event> findByStatus(Event.EventStatus status);
    
    @Query(value = "SELECT e.* FROM events e " +
           "WHERE e.status = 'UPCOMING' " +
           "AND e.is_public = true " +
           "AND e.event_date > :now " +
           "AND ST_DWithin(e.location::geography, CAST(:userLocation AS geography), :radiusMeters) " +
           "ORDER BY ST_Distance(e.location::geography, CAST(:userLocation AS geography)) " +
           "LIMIT :limit",
           nativeQuery = true)
    List<Event> findNearbyUpcomingEvents(
        @Param("userLocation") Point userLocation,
        @Param("radiusMeters") double radiusMeters,
        @Param("now") LocalDateTime now,
        @Param("limit") int limit
    );
    
    @EntityGraph(attributePaths = {"organizer", "participants"})
    @Query("SELECT e FROM Event e WHERE e.eventDate BETWEEN :start AND :end AND e.status = 'UPCOMING'")
    List<Event> findUpcomingEventsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    @Query("SELECT COUNT(e) FROM Event e WHERE e.organizer.id = :userId AND e.status IN ('UPCOMING', 'IN_PROGRESS')")
    long countActiveEventsByOrganizer(@Param("userId") Long userId);
    
    @EntityGraph(attributePaths = {"organizer", "participants"})
    List<Event> findByOrganizerId(Long organizerId);
    
    @EntityGraph(attributePaths = {"organizer", "participants"})
    @Query("SELECT e FROM Event e JOIN e.participants p WHERE p.id = :userId AND e.status = 'UPCOMING'")
    List<Event> findEventsByParticipant(@Param("userId") Long userId);
}



