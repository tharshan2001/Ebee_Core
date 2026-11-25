package com.ebee.importer.service;

import java.util.List;

/** One import type. validate() must not change anything; apply() runs only when validate found no errors. */
public interface RowImporter {

    ImportType type();

    /** Check every row (and the file as a whole, e.g. duplicates), adding errors. */
    void validate(List<ImportRow> rows, List<RowError> errors);

    /** Write the rows. Runs inside one transaction with the others: all rows or none. */
    Counts apply(List<ImportRow> rows);

    record Counts(int created, int updated, int skipped) {
    }
}
