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

/**
 * Import flow: read the file → check columns → validate every row → (dry run: stop here) →
 * apply all rows in one transaction. Nothing is written while there's any error.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    static final int MAX_ERRORS_RETURNED = 200;
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
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload a non-empty .csv or .xlsx file");
        }
        TabularFileReader.Parsed parsed = TabularFileReader.read(file.getOriginalFilename(), file.getInputStream());
        return run(type, file.getOriginalFilename(), parsed, dryRun);
    }

    ImportResult run(ImportType type, String fileName, TabularFileReader.Parsed parsed, boolean dryRun) {
        RowImporter importer = importers.get(type);
        if (importer == null) {
            throw new IllegalArgumentException("No importer for " + type);
        }
        List<RowError> errors = new ArrayList<>();
        for (String col : type.requiredColumns()) {
            if (!parsed.headers().contains(col)) {
                errors.add(new RowError(1, col, "column is missing (download the template for the expected headers)"));
            }
        }
        List<String> ignored = parsed.headers().stream()
                .filter(h -> !h.isBlank() && !type.allColumns().contains(h)).toList();
        if (parsed.rows().isEmpty() && errors.isEmpty()) {
            errors.add(new RowError(2, null, "the file has no data rows"));
        }
        List<RowError> notices = new ArrayList<>();
        RowImporter.Counts preview = NOTHING;
        if (errors.isEmpty()) {
            preview = importer.validate(parsed.rows(), errors, notices);
        }

        int total = parsed.rows().size();
        if (!errors.isEmpty() || dryRun) {
            ImportJob.Status status = errors.isEmpty() ? ImportJob.Status.VALID : ImportJob.Status.INVALID;
            return finish(type, fileName, dryRun, status, total, errors.isEmpty() ? preview : NOTHING, errors, notices,
                    ignored);
        }
        try {
            RowImporter.Counts counts = applier.apply(importer, parsed.rows());
            return finish(type, fileName, false, ImportJob.Status.APPLIED, total, counts, errors, notices, ignored);
        } catch (RuntimeException e) {
            log.warn("Import {} of '{}' failed and was rolled back: {}", type, fileName, e.getMessage());
            errors.add(new RowError(0, null, "Nothing was imported: " + e.getMessage()));
            return finish(type, fileName, false, ImportJob.Status.FAILED, total, NOTHING, errors, List.of(), ignored);
        }
    }

    private ImportResult finish(ImportType type, String fileName, boolean dryRun, ImportJob.Status status, int total,
                                RowImporter.Counts counts, List<RowError> errors, List<RowError> notices,
                                List<String> ignored) {
        List<RowError> shown = errors.size() > MAX_ERRORS_RETURNED ? errors.subList(0, MAX_ERRORS_RETURNED) : errors;
        ImportJob job = jobRepository.save(new ImportJob(type.name(), fileName, dryRun, status, total, counts.created(),
                counts.updated(), counts.skipped(), errors.size(), toJson(shown), securityService.getCurrentUserId()));
        return new ImportResult(job.getId(), type, dryRun, status, total, counts.created(), counts.updated(),
                counts.skipped(), errors.size(), shown,
                notices.size() > MAX_ERRORS_RETURNED ? notices.subList(0, MAX_ERRORS_RETURNED) : notices, ignored);
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
