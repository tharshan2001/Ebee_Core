package com.ebee.importer.service;

import com.ebee.importer.entity.ImportJob;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outcome of an import. On a dry run nothing is written; status VALID means "apply would work" and
 * INVALID means some rows (or the whole file) have problems. The counts then say what applying the
 * good rows would do, {@code invalidRows} how many would be left out, and {@code notices} which rows
 * change existing records or are only partly applied. {@code columns} and {@code rows} echo the file
 * back on a dry run, so each problem can be shown against its row.
 */
public record ImportResult(UUID jobId, ImportType type, boolean dryRun, ImportJob.Status status, int totalRows,
                           int created, int updated, int skipped, int errorCount, List<RowError> errors,
                           List<RowError> notices, List<String> ignoredColumns, int invalidRows,
                           List<String> columns, List<PreviewRow> rows) {

    public record PreviewRow(int row, Map<String, String> values) {
    }
}
