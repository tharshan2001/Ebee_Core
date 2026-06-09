package com.ebee.backup.repository;

import com.ebee.backup.entity.BackupAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BackupAuditLogRepository extends JpaRepository<BackupAuditLog, UUID> {
    List<BackupAuditLog> findAllByOrderByCreatedAtDesc();
}
