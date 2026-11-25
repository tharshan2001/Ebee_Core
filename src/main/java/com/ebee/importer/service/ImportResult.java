package com.ebee.importer.service;

import com.ebee.importer.entity.ImportJob;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of an import. On a dry run nothing is written; status VALID means "apply would work".
 * When there are errors nothing is written either: fix them and upload again.
 */
public record ImportResult(UUID jobId, ImportType type, boolean dryRun, ImportJob.Status status, int totalRows,
                           int created, int updated, int skipped, int errorCount, List<RowError> errors,
                           List<String> ignoredColumns) {
}
