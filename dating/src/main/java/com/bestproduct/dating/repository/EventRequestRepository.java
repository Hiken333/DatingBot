package com.bestproduct.dating.repository;

import com.bestproduct.dating.domain.entity.EventRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRequestRepository extends JpaRepository<EventRequest, Long> {
    
    Optional<EventRequest> findByEventIdAndUserId(Long eventId, Long userId);
    
    List<EventRequest> findByEventId(Long eventId);
    
    List<EventRequest> findByEventIdAndStatus(Long eventId, EventRequest.RequestStatus status);
    
    List<EventRequest> findByUserId(Long userId);
    
    List<EventRequest> findByStatus(EventRequest.RequestStatus status);
    
    @Query("SELECT er FROM EventRequest er WHERE er.event.organizer.id = :organizerId AND er.status = 'PENDING'")
    List<EventRequest> findPendingRequestsForOrganizer(@Param("organizerId") Long organizerId);
}



