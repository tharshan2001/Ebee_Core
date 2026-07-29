package com.ebee.reporting.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.reporting.dto.AgingReportResponse;
import com.ebee.reporting.dto.ReportSummaryResponse;
import com.ebee.reporting.dto.TrialBalanceResponse;
import com.ebee.reporting.service.ReportsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class UnifiedReportsController {

    private final ReportsService reportsService;

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReportSummaryResponse>> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        ReportSummaryResponse response = reportsService.getSummary(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Report summary retrieved successfully", response));
    }

    @GetMapping("/trial-balance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TrialBalanceResponse>> getTrialBalance() {
        TrialBalanceResponse response = reportsService.getTrialBalance();
        return ResponseEntity.ok(ApiResponse.success("Trial balance retrieved successfully", response));
    }

    @GetMapping("/aging")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AgingReportResponse>> getAgingReport() {
        AgingReportResponse response = reportsService.getAgingReport();
        return ResponseEntity.ok(ApiResponse.success("Aging report retrieved successfully", response));
    }
}
