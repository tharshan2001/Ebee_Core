package com.ebee.importer.service;

import com.ebee.importer.entity.ImportJob;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of an import. On a dry run nothing is written; status VALID means "apply would work".
 * When there are errors nothing is written either: fix them and upload again.
 * The counts of a dry run say what applying would do; {@code notices} lists the rows that change
 * existing records or are only partly applied.
 */
public record ImportResult(UUID jobId, ImportType type, boolean dryRun, ImportJob.Status status, int totalRows,
                           int created, int updated, int skipped, int errorCount, List<RowError> errors,
                           List<RowError> notices, List<String> ignoredColumns) {
}
