package com.ebee.backup.service;

import com.ebee.backup.entity.BackupAuditLog;
import com.ebee.backup.enums.BackupAction;
import com.ebee.backup.repository.BackupAuditLogRepository;
import com.ebee.common.security.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BackupAuditLogService {

    private final BackupAuditLogRepository repository;
    private final SecurityService securityService;

    public BackupAuditLog record(BackupAction action, String filename, String detail) {
        BackupAuditLog log = BackupAuditLog.builder()
                .action(action)
                .filename(filename)
                .detail(detail)
                .performedBy(securityService.getCurrentUserId())
                .build();
        return repository.save(log);
    }

    public List<BackupAuditLog> getRecentLogs() {
        return repository.findAllByOrderByCreatedAtDesc();
    }
}
