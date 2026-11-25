package com.ebee.importer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_jobs")
public class ImportJob {

    public enum Status { VALID, INVALID, APPLIED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "import_type", nullable = false, length = 30)
    private String importType;
    @Column(name = "file_name")
    private String fileName;
    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;
    @Column(nullable = false, length = 20)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status;
    @Column(name = "total_rows", nullable = false)
    private int totalRows;
    @Column(name = "created_count", nullable = false)
    private int createdCount;
    @Column(name = "updated_count", nullable = false)
    private int updatedCount;
    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;
    @Column(name = "error_count", nullable = false)
    private int errorCount;
    @Column(columnDefinition = "TEXT")
    private String errors;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ImportJob() {
    }

    public ImportJob(String importType, String fileName, boolean dryRun, Status status, int totalRows, int createdCount,
                     int updatedCount, int skippedCount, int errorCount, String errors, UUID createdBy) {
        this.importType = importType;
        this.fileName = fileName;
        this.dryRun = dryRun;
        this.status = status;
        this.totalRows = totalRows;
        this.createdCount = createdCount;
        this.updatedCount = updatedCount;
        this.skippedCount = skippedCount;
        this.errorCount = errorCount;
        this.errors = errors;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public String getImportType() { return importType; }
    public String getFileName() { return fileName; }
    public boolean isDryRun() { return dryRun; }
    public Status getStatus() { return status; }
    public int getTotalRows() { return totalRows; }
    public int getCreatedCount() { return createdCount; }
    public int getUpdatedCount() { return updatedCount; }
    public int getSkippedCount() { return skippedCount; }
    public int getErrorCount() { return errorCount; }
    public String getErrors() { return errors; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
