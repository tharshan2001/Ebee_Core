package com.ebee.audit.repository;

import com.ebee.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    @Query("SELECT a.action, COUNT(a), COUNT(DISTINCT a.performedBy) FROM AuditLog a WHERE a.createdAt >= :start AND a.createdAt < :end GROUP BY a.action ORDER BY COUNT(a) DESC")
    List<Object[]> findActionSummaryByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
