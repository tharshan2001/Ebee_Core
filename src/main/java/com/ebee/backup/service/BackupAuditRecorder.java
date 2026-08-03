package com.ebee.backup.service;

import com.ebee.backup.enums.BackupAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackupAuditRecorder {

    private final BackupAuditLogService auditLogService;

    public void recordCreate(String filename) {
        try {
            auditLogService.record(BackupAction.CREATE, filename, null);
        } catch (Exception e) {
            log.warn("Failed to record backup create audit: {}", e.getMessage());
        }
    }

    public void recordRestore(String filename, String preRestoreBackup) {
        try {
            auditLogService.record(BackupAction.RESTORE, filename,
                    "Pre-restore backup: " + preRestoreBackup);
        } catch (Exception e) {
            log.warn("Failed to record backup restore audit: {}", e.getMessage());
        }
    }

    public void recordUpload(String filename, String note) {
        try {
            auditLogService.record(BackupAction.UPLOAD, filename, note);
        } catch (Exception e) {
            log.warn("Failed to record backup upload audit: {}", e.getMessage());
        }
    }

    public void recordDownload(String filename) {
        try {
            auditLogService.record(BackupAction.DOWNLOAD, filename, null);
        } catch (Exception e) {
            log.warn("Failed to record backup download audit: {}", e.getMessage());
        }
    }
}
