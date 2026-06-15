package com.ebee.commission.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.commission.dto.BatchPaymentRequest;
import com.ebee.commission.dto.BatchPaymentResponse;
import com.ebee.commission.dto.CommissionCalculateRequest;
import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.dto.PendingPaymentSummary;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.commission.service.CommissionCalculationService;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.commission.service.CommissionQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/commissions")
@RequiredArgsConstructor
public class CommissionController {

    private final CommissionCalculationService calculationService;
    private final CommissionLifecycleService lifecycleService;
    private final CommissionQueryService queryService;
    private final CommissionRepository commissionRepository;
    private final CommissionMapper mapper;

    @PostMapping("/calculate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionResponse>> calculateCommission(
            @Valid @RequestBody CommissionCalculateRequest request) {
        CommissionResponse response = calculationService.calculateCommission(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Commission calculated successfully", response));
    }

    @PutMapping("/{id}/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionResponse>> markAsProcessing(@PathVariable UUID id) {
        CommissionResponse response = lifecycleService.markAsProcessing(id);
        return ResponseEntity.ok(ApiResponse.success("Commission marked as processing", response));
    }

    @PutMapping("/{id}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionResponse>> markAsPaid(@PathVariable UUID id) {
        CommissionResponse response = lifecycleService.markAsPaid(id);
        return ResponseEntity.ok(ApiResponse.success("Commission marked as paid", response));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionResponse>> rejectCommission(
            @PathVariable UUID id,
            @RequestParam String reason) {
        CommissionResponse response = lifecycleService.rejectCommission(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Commission rejected", response));
    }

    @GetMapping("/agent/{agentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CommissionResponse>>> getCommissionsByAgent(
            @PathVariable UUID agentId) {
        List<CommissionResponse> response = queryService.getCommissionsByAgent(agentId);
        return ResponseEntity.ok(ApiResponse.success("Commissions retrieved successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<CommissionResponse>>> getAllCommissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "calculatedAt,desc") String sort) {
        Sort.Direction direction = sort.endsWith(",asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = sort.replace(",asc", "").replace(",desc", "");
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
        Page<CommissionResponse> response = queryService.getAllCommissions(pageable);
        return ResponseEntity.ok(ApiResponse.success("Commissions retrieved successfully", response));
    }

    @GetMapping("/pending-payment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PendingPaymentSummary>> getPendingPaymentSummary(
            @RequestParam UUID agentId) {
        PendingPaymentSummary response = queryService.getPendingPaymentSummary(agentId);
        return ResponseEntity.ok(ApiResponse.success("Pending payment summary retrieved successfully", response));
    }

    @PostMapping("/pay-batch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BatchPaymentResponse>> payBatch(
            @Valid @RequestBody BatchPaymentRequest request) {
        BatchPaymentResponse response = lifecycleService.payBatch(request.getCommissionIds());
        return ResponseEntity.ok(ApiResponse.success(
                "Batch payment of LKR " + response.getTotalPaid() + " completed. "
                + response.getCommissionsPaid() + " commission(s) marked as paid.",
                response));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CommissionResponse>>> getCommissionsByStatus(
            @PathVariable String status) {
        List<CommissionResponse> response = queryService.getCommissionsByStatus(
                com.ebee.commission.enums.CommissionStatus.valueOf(status.toUpperCase())
        );
        return ResponseEntity.ok(ApiResponse.success("Commissions retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionResponse>> getCommissionById(@PathVariable UUID id) {
        CommissionResponse response = queryService.getCommissionById(id);
        return ResponseEntity.ok(ApiResponse.success("Commission retrieved successfully", response));
    }
}
