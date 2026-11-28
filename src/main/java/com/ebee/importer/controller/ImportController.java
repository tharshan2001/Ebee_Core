package com.ebee.importer.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.importer.service.ImportResult;
import com.ebee.importer.service.ImportService;
import com.ebee.importer.service.ImportType;
import com.ebee.importer.entity.ImportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Bulk import from CSV or Excel. Types: products, opening-stock, customers, suppliers.
 * Upload with dryRun=true (the default) to check the file; upload again with dryRun=false to apply,
 * adding skipInvalid=true to leave out the rows with problems.
 */
@RestController
@RequestMapping("/api/imports")
@PreAuthorize("hasRole('ADMIN')")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping(value = "/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportResult>> upload(@PathVariable String type,
                                                            @RequestParam("file") MultipartFile file,
                                                            @RequestParam(defaultValue = "true") boolean dryRun,
                                                            @RequestParam(defaultValue = "false") boolean skipInvalid)
            throws IOException {
        ImportResult result = importService.run(ImportType.fromPath(type), file, dryRun, skipInvalid);
        String message = switch (result.status()) {
            case VALID -> "File is valid: " + result.totalRows() + " rows ready to import";
            case INVALID -> result.errorCount() + " problem(s) found; nothing was imported";
            case APPLIED -> "Imported " + (result.totalRows() - result.invalidRows()) + " rows"
                    + (result.invalidRows() > 0 ? "; " + result.invalidRows() + " left out" : "");
            case FAILED -> "Import failed; nothing was imported";
        };
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    @GetMapping("/{type}/template")
    public ResponseEntity<byte[]> template(@PathVariable String type) {
        ImportType t = ImportType.fromPath(type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + t.name().toLowerCase() + "-template.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(t.templateCsv().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ImportJob>>> history(@RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Import history",
                importService.history(PageRequest.of(page, Math.min(size, 100)))));
    }
}
