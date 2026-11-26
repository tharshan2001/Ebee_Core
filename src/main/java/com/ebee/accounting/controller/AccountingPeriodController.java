package com.ebee.accounting.controller;

import com.ebee.accounting.dto.AccountingPeriodResponse;
import com.ebee.accounting.dto.PeriodActionRequest;
import com.ebee.accounting.dto.PeriodSummaryResponse;
import com.ebee.accounting.entity.AccountingPeriod;
import com.ebee.accounting.service.AccountingPeriodService;
import com.ebee.accounting.service.PeriodSummaryService;
import com.ebee.common.response.ApiResponse;
import com.ebee.common.security.SecurityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounting/periods")
@RequiredArgsConstructor
public class AccountingPeriodController {

    private final AccountingPeriodService accountingPeriodService;
    private final SecurityService securityService;
    private final PeriodSummaryService periodSummaryService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AccountingPeriodResponse>>> getAllPeriods() {
        List<AccountingPeriod> periods = accountingPeriodService.getAllPeriods();
        List<AccountingPeriodResponse> response = periods.stream()
            .map(this::toResponse)
            .toList();
        return ResponseEntity.ok(ApiResponse.success("Periods retrieved", response));
    }

    @GetMapping("/year/{year}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AccountingPeriodResponse>>> getPeriodsByYear(@PathVariable Integer year) {
        List<AccountingPeriod> periods = accountingPeriodService.getPeriodsByYear(year);
        List<AccountingPeriodResponse> response = periods.stream()
            .map(this::toResponse)
            .toList();
        return ResponseEntity.ok(ApiResponse.success("Periods for year retrieved", response));
    }

    @GetMapping("/month/{month}/year/{year}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AccountingPeriodResponse>> getPeriod(
            @PathVariable Integer month,
            @PathVariable Integer year) {
        return accountingPeriodService.getPeriodByMonthAndYear(month, year)
            .map(period -> ResponseEntity.ok(ApiResponse.success("Period found", toResponse(period))))
            .orElse(ResponseEntity.ok(ApiResponse.success("Period not found", null)));
    }

    /** What was posted in this period, by kind, and its status history (for the month-close screen). */
    @GetMapping("/{id}/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PeriodSummaryResponse>> getPeriodSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Period summary", periodSummaryService.summarize(id)));
    }

    @GetMapping("/open")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AccountingPeriodResponse>>> getOpenPeriods() {
        List<AccountingPeriod> periods = accountingPeriodService.getOpenPeriods();
        List<AccountingPeriodResponse> response = periods.stream()
            .map(this::toResponse)
            .toList();
        return ResponseEntity.ok(ApiResponse.success("Open periods retrieved", response));
    }

    @PostMapping("/open")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AccountingPeriodResponse>> openPeriod(
            @RequestParam Integer year,
            @RequestParam Integer month) {
        UUID currentUserId = securityService.getCurrentUserId();
        AccountingPeriod period = accountingPeriodService.openPeriod(year, month, currentUserId);
        return ResponseEntity.ok(ApiResponse.success("Period opened", toResponse(period)));
    }

    @PostMapping("/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AccountingPeriodResponse>> closePeriod(
            @Valid @RequestBody PeriodActionRequest request) {
        UUID currentUserId = securityService.getCurrentUserId();
        AccountingPeriod period = accountingPeriodService.closePeriod(
            request.periodId(),
            currentUserId,
            request.reason()
        );
        return ResponseEntity.ok(ApiResponse.success("Period closed", toResponse(period)));
    }

    @PostMapping("/reopen")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AccountingPeriodResponse>> reopenPeriod(
            @Valid @RequestBody PeriodActionRequest request) {
        UUID currentUserId = securityService.getCurrentUserId();
        AccountingPeriod period = accountingPeriodService.reopenPeriod(
            request.periodId(),
            currentUserId,
            request.reason()
        );
        return ResponseEntity.ok(ApiResponse.success("Period reopened", toResponse(period)));
    }

    @PostMapping("/archive")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AccountingPeriodResponse>> archivePeriod(
            @Valid @RequestBody PeriodActionRequest request) {
        UUID currentUserId = securityService.getCurrentUserId();
        AccountingPeriod period = accountingPeriodService.archivePeriod(
            request.periodId(),
            currentUserId,
            request.reason()
        );
        return ResponseEntity.ok(ApiResponse.success("Period archived", toResponse(period)));
    }

    private AccountingPeriodResponse toResponse(AccountingPeriod period) {
        return new AccountingPeriodResponse(
            period.getId(),
            period.getMonth(),
            period.getYear(),
            period.getStatus(),
            period.getOpenedAt(),
            period.getClosedAt(),
            null
        );
    }
}
