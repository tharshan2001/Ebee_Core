package com.ebee.backup.controller;

import com.ebee.auth.service.OtpService;
import com.ebee.backup.entity.BackupAuditLog;
import com.ebee.backup.enums.BackupAction;
import com.ebee.backup.service.BackupAuditLogService;
import com.ebee.backup.service.BackupService;
import com.ebee.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/db")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {

    private final BackupService backupService;
    private final BackupAuditLogService auditLogService;
    private final OtpService otpService;

    @GetMapping("/backups")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listBackups() {
        List<Map<String, Object>> backups = backupService.listBackups();
        return ResponseEntity.ok(ApiResponse.success("Backups retrieved successfully", backups));
    }

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendOtp(Authentication authentication) {
        otpService.generateAndSendOtp(authentication.getName(), "backup operation");
        return ResponseEntity.ok(ApiResponse.success("OTP sent to your email", null));
    }

    @PostMapping("/backup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createBackup(
            Authentication authentication,
            @RequestParam String otp) {
        verifyOtp(authentication, otp);
        Map<String, Object> backup = backupService.createBackup();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Backup created successfully", backup));
    }

    @GetMapping("/backups/{filename}")
    public ResponseEntity<InputStreamResource> downloadBackup(
            Authentication authentication,
            @PathVariable String filename,
            @RequestParam String otp) {
        verifyOtp(authentication, otp);
        Path file = backupService.getBackupFile(filename);
        auditLogService.record(BackupAction.DOWNLOAD, filename, null);
        try {
            InputStreamResource resource = new InputStreamResource(new FileInputStream(file.toFile()));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(Files.size(file))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getFileName().toString() + "\"")
                    .body(resource);
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/backups/upload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadBackup(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam String otp) {
        verifyOtp(authentication, otp);
        Map<String, Object> result = backupService.uploadBackup(file);
        return ResponseEntity.ok(ApiResponse.success("Backup uploaded successfully", result));
    }

    @PostMapping("/restore")
    public ResponseEntity<ApiResponse<Map<String, Object>>> restoreBackup(
            Authentication authentication,
            @RequestParam String filename,
            @RequestParam String otp) {
        verifyOtp(authentication, otp);
        Map<String, Object> result = backupService.restore(filename);
        return ResponseEntity.ok(ApiResponse.success(
                "Restore complete. Pre-restore backup: " + result.get("preRestoreBackup"), result));
    }

    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<List<BackupAuditLog>>> getAuditLogs() {
        List<BackupAuditLog> logs = auditLogService.getRecentLogs();
        return ResponseEntity.ok(ApiResponse.success("Audit logs retrieved", logs));
    }

    private void verifyOtp(Authentication authentication, String otp) {
        if (!otpService.verifyOtp(authentication.getName(), otp)) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }
    }
}
