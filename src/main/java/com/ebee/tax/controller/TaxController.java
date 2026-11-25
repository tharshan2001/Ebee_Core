package com.ebee.tax.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.tax.dto.BusinessSettingsRequest;
import com.ebee.tax.dto.BusinessSettingsResponse;
import com.ebee.tax.dto.TaxRateRequest;
import com.ebee.tax.dto.TaxRateResponse;
import com.ebee.tax.dto.VatReport;
import com.ebee.tax.service.TaxService;
import com.ebee.tax.service.VatService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tax")
public class TaxController {

    private final TaxService taxService;
    private final VatService vatService;

    public TaxController(TaxService taxService, VatService vatService) {
        this.taxService = taxService;
        this.vatService = vatService;
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<ApiResponse<BusinessSettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success("Tax settings", BusinessSettingsResponse.of(taxService.getSettings())));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BusinessSettingsResponse>> updateSettings(@RequestBody BusinessSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Tax settings updated",
                BusinessSettingsResponse.of(taxService.updateSettings(request))));
    }

    @GetMapping("/rates")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<ApiResponse<List<TaxRateResponse>>> listRates() {
        return ResponseEntity.ok(ApiResponse.success("Tax rates",
                taxService.listRates().stream().map(TaxRateResponse::of).toList()));
    }

    @PostMapping("/rates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TaxRateResponse>> createRate(@Valid @RequestBody TaxRateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tax rate created",
                TaxRateResponse.of(taxService.createRate(request))));
    }

    @PutMapping("/rates/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TaxRateResponse>> updateRate(@PathVariable UUID id,
                                                                   @Valid @RequestBody TaxRateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Tax rate updated", TaxRateResponse.of(taxService.updateRate(id, request))));
    }

    /** VAT return: output VAT (sales − credit notes) minus input VAT (purchases − returns). */
    @GetMapping("/vat-report")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VatReport>> vatReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("VAT report", vatService.report(from, to)));
    }
}
