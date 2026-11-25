package com.ebee.importer.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Applies a validated file in one transaction: every row, or none if anything fails. */
@Component
public class ImportApplier {

    @Transactional
    public RowImporter.Counts apply(RowImporter importer, List<ImportRow> rows) {
        return importer.apply(rows);
    }
}
