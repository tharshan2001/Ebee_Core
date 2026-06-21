package com.ebee.backup.repository;

import com.ebee.backup.entity.BackupAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BackupAuditLogRepository extends JpaRepository<BackupAuditLog, UUID> {
    List<BackupAuditLog> findAllByOrderByCreatedAtDesc();
}
