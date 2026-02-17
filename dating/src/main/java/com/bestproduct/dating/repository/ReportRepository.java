package com.bestproduct.dating.repository;

import com.bestproduct.dating.domain.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    
    List<Report> findByStatus(Report.ReportStatus status);
    
    List<Report> findByReportedId(Long reportedId);
    
    List<Report> findByReporterId(Long reporterId);
    
    @Query("SELECT COUNT(r) FROM Report r WHERE r.reported.id = :userId AND r.status != 'DISMISSED'")
    long countValidReportsForUser(@Param("userId") Long userId);
    
    @Query("SELECT r FROM Report r WHERE r.status = 'PENDING' ORDER BY r.createdAt ASC")
    List<Report> findPendingReportsOrderedByDate();
}



