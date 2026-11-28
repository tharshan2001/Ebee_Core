package com.ebee.importer.service;

import java.util.List;

/** One import type. validate() must not change anything; apply() runs only when validate found no errors. */
public interface RowImporter {

    ImportType type();

    /**
     * Check every row (and the file as a whole, e.g. duplicates), adding errors, and report what
     * applying would do: the returned counts, plus {@code notices} for rows that touch existing
     * records or where something in the row won't be applied.
     */
    Counts validate(List<ImportRow> rows, List<RowError> errors, List<RowError> notices);

    /** Write the rows. Runs inside one transaction with the others: all rows or none. */
    Counts apply(List<ImportRow> rows);

    record Counts(int created, int updated, int skipped) {
    }
}
