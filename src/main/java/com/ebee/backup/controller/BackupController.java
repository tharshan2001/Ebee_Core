package com.ebee.backup.controller;

import com.ebee.backup.service.BackupService;
import com.ebee.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping("/backups")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listBackups() {
        List<Map<String, Object>> backups = backupService.listBackups();
        return ResponseEntity.ok(ApiResponse.success("Backups retrieved successfully", backups));
    }

    @PostMapping("/backup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createBackup() {
        Map<String, Object> backup = backupService.createBackup();
        return ResponseEntity.ok(ApiResponse.success("Backup created successfully", backup));
    }

    @GetMapping("/backups/{filename}")
    public ResponseEntity<InputStreamResource> downloadBackup(@PathVariable String filename) {
        Path file = backupService.getBackupFile(filename);
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
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = backupService.uploadBackup(file);
        return ResponseEntity.ok(ApiResponse.success("Backup uploaded successfully", result));
    }

    @PostMapping("/restore")
    public ResponseEntity<ApiResponse<Map<String, Object>>> restoreBackup(
            @RequestParam String filename) {
        Map<String, Object> result = backupService.restore(filename);
        return ResponseEntity.ok(ApiResponse.success(
                "Restore complete. Pre-restore backup: " + result.get("preRestoreBackup"), result));
    }
}
