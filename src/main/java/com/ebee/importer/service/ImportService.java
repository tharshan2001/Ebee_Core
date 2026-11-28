package com.ebee.importer.service;

import com.ebee.common.security.SecurityService;
import com.ebee.importer.entity.ImportJob;
import com.ebee.importer.repository.ImportJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Import flow: read the file → check columns → validate every row → (dry run: stop here) →
 * apply in one transaction. Rows with problems block the import unless the caller asks to skip them.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    static final int MAX_ERRORS_STORED = 200;
    private static final RowImporter.Counts NOTHING = new RowImporter.Counts(0, 0, 0);

    private final Map<ImportType, RowImporter> importers = new EnumMap<>(ImportType.class);
    private final ImportApplier applier;
    private final ImportJobRepository jobRepository;
    private final SecurityService securityService;
    private final ObjectMapper objectMapper;

    public ImportService(List<RowImporter> importers, ImportApplier applier, ImportJobRepository jobRepository,
                         SecurityService securityService, ObjectMapper objectMapper) {
        importers.forEach(i -> this.importers.put(i.type(), i));
        this.applier = applier;
        this.jobRepository = jobRepository;
        this.securityService = securityService;
        this.objectMapper = objectMapper;
    }

    public ImportResult run(ImportType type, MultipartFile file, boolean dryRun) throws IOException {
        return run(type, file, dryRun, false);
    }

    /** @param skipInvalid when applying, leave out rows with problems and import the others */
    public ImportResult run(ImportType type, MultipartFile file, boolean dryRun, boolean skipInvalid) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload a non-empty .csv or .xlsx file");
        }
        TabularFileReader.Parsed parsed = TabularFileReader.read(file.getOriginalFilename(), file.getInputStream());
        return run(type, file.getOriginalFilename(), parsed, dryRun, skipInvalid);
    }

    /** The rows that pass the checks together, and what applying them would do. */
    private record Checked(List<ImportRow> good, RowImporter.Counts counts, List<RowError> errors,
                           List<RowError> notices) {
    }

    /**
     * Checks the rows, then re-checks what is left after dropping the bad ones (rows are checked
     * against each other too) until the remainder is clean. {@code errors} has every dropped row's
     * problems; {@code notices} describes the first, complete pass.
     */
    private Checked check(RowImporter importer, List<ImportRow> rows) {
        List<RowError> errors = new ArrayList<>();
        List<RowError> notices = new ArrayList<>();
        List<ImportRow> good = rows;
        RowImporter.Counts counts = importer.validate(good, errors, notices);
        List<RowError> found = errors;
        while (!found.isEmpty()) {
            Set<Integer> bad = found.stream().map(RowError::row).collect(Collectors.toSet());
            List<ImportRow> rest = good.stream().filter(r -> !bad.contains(r.rowNumber())).toList();
            if (rest.isEmpty() || rest.size() == good.size()) {
                return new Checked(List.of(), NOTHING, errors, notices);
            }
            good = rest;
            found = new ArrayList<>();
            counts = importer.validate(good, found, new ArrayList<>());
            errors.addAll(found);
        }
        return new Checked(good, counts, errors, notices);
    }

    ImportResult run(ImportType type, String fileName, TabularFileReader.Parsed parsed, boolean dryRun,
                     boolean skipInvalid) {
        RowImporter importer = importers.get(type);
        if (importer == null) {
            throw new IllegalArgumentException("No importer for " + type);
        }
        // Problems with the file as a whole: nothing can be imported.
        List<RowError> fileErrors = new ArrayList<>();
        for (String col : type.requiredColumns()) {
            if (!parsed.headers().contains(col)) {
                fileErrors.add(new RowError(1, col, "column is missing (download the template for the expected headers)"));
            }
        }
        List<String> ignored = parsed.headers().stream()
                .filter(h -> !h.isBlank() && !type.allColumns().contains(h)).toList();
        if (parsed.rows().isEmpty() && fileErrors.isEmpty()) {
            fileErrors.add(new RowError(2, null, "the file has no data rows"));
        }
        int total = parsed.rows().size();
        Checked checked = fileErrors.isEmpty()
                ? check(importer, parsed.rows())
                : new Checked(List.of(), NOTHING, fileErrors, List.of());
        int invalid = fileErrors.isEmpty() ? total - checked.good().size() : total;
        List<RowError> errors = new ArrayList<>(checked.errors());
        Outcome outcome = new Outcome(type, fileName, dryRun, total, invalid, errors, checked.notices(), ignored, parsed);

        if (dryRun) {
            return finish(outcome, errors.isEmpty() ? ImportJob.Status.VALID : ImportJob.Status.INVALID, checked.counts());
        }
        if (checked.good().isEmpty() || (!errors.isEmpty() && !skipInvalid)) {
            return finish(outcome, ImportJob.Status.INVALID, NOTHING);
        }
        try {
            return finish(outcome, ImportJob.Status.APPLIED, applier.apply(importer, checked.good()));
        } catch (RuntimeException e) {
            log.warn("Import {} of '{}' failed and was rolled back: {}", type, fileName, e.getMessage());
            errors.add(new RowError(0, null, "Nothing was imported: " + e.getMessage()));
            return finish(outcome, ImportJob.Status.FAILED, NOTHING);
        }
    }

    private record Outcome(ImportType type, String fileName, boolean dryRun, int total, int invalidRows,
                           List<RowError> errors, List<RowError> notices, List<String> ignored,
                           TabularFileReader.Parsed parsed) {
    }

    private ImportResult finish(Outcome o, ImportJob.Status status, RowImporter.Counts counts) {
        List<RowError> stored = o.errors().size() > MAX_ERRORS_STORED
                ? o.errors().subList(0, MAX_ERRORS_STORED) : o.errors();
        ImportJob job = jobRepository.save(new ImportJob(o.type().name(), o.fileName(), o.dryRun(), status, o.total(),
                counts.created(), counts.updated(), counts.skipped(), o.errors().size(), toJson(stored),
                securityService.getCurrentUserId()));
        // The file is echoed back only for the check, which is what the screen draws its table from.
        List<String> columns = o.dryRun()
                ? o.parsed().headers().stream().filter(h -> !h.isBlank()).distinct().toList() : List.of();
        List<ImportResult.PreviewRow> rows = o.dryRun()
                ? o.parsed().rows().stream().map(r -> new ImportResult.PreviewRow(r.rowNumber(), r.values())).toList()
                : List.of();
        // Warn about the template's example row; it is valid data, so it is not an error.
        List<RowError> notices = new ArrayList<>(o.notices());
        int samples = 0;
        for (ImportRow row : o.parsed().rows()) {
            if (o.type().isSampleRow(row)) {
                samples++;
                notices.add(new RowError(row.rowNumber(), null, "this is the template's example row, not your data"));
            }
        }
        return new ImportResult(job.getId(), o.type(), o.dryRun(), status, o.total(), counts.created(),
                counts.updated(), counts.skipped(), o.errors().size(), o.errors(), notices, o.ignored(),
                o.invalidRows(), columns, rows, samples);
    }

    private String toJson(List<RowError> errors) {
        if (errors.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Page<ImportJob> history(Pageable pageable) {
        return jobRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
