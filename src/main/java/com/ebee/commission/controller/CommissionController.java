package com.ebee.commission.controller;

import com.ebee.agent.dto.AgentResponse;
import com.ebee.agent.service.AgentService;
import com.ebee.common.response.ApiResponse;
import com.ebee.common.response.PageResponse;
import com.ebee.common.security.SecurityService;
import com.ebee.common.util.PaginationUtils;
import com.ebee.common.util.SortUtils;
import com.ebee.common.util.SortableFields;
import com.ebee.commission.dto.BatchPaymentRequest;
import com.ebee.commission.dto.BatchPaymentResponse;
import com.ebee.commission.dto.CommissionCalculateRequest;
import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.dto.PendingPaymentSummary;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.service.CommissionCalculationService;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.commission.service.CommissionQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final CommissionMapper mapper;
    private final SecurityService securityService;
    private final AgentService agentService;

    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CommissionResponse>> calculateCommission(
            @Valid @RequestBody CommissionCalculateRequest request) {
        CommissionResponse response = calculationService.calculateCommission(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Commission calculated successfully", response));
    }

    @PutMapping("/{id}/process")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CommissionResponse>> markAsProcessing(@PathVariable UUID id) {
        CommissionResponse response = lifecycleService.markAsProcessing(id);
        return ResponseEntity.ok(ApiResponse.success("Commission marked as processing", response));
    }

    @PutMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CommissionResponse>> markAsPaid(@PathVariable UUID id) {
        CommissionResponse response = lifecycleService.markAsPaid(id);
        return ResponseEntity.ok(ApiResponse.success("Commission marked as paid", response));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CommissionResponse>> rejectCommission(
            @PathVariable UUID id,
            @RequestParam String reason) {
        CommissionResponse response = lifecycleService.rejectCommission(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Commission rejected", response));
    }

    @GetMapping("/agent/{agentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<CommissionResponse>>> getCommissionsByAgent(
            @PathVariable UUID agentId) {
        List<CommissionResponse> response = queryService.getCommissionsByAgent(agentId);
        return ResponseEntity.ok(ApiResponse.success("Commissions retrieved successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<CommissionResponse>>> getAllCommissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "calculatedAt,desc") String sort) {
        Pageable pageable = PageRequest.of(
                PaginationUtils.safePage(page),
                PaginationUtils.safeSize(size),
                SortUtils.parse(sort, SortableFields.AllowedFor.COMMISSION));
        Page<CommissionResponse> commissionPage = queryService.getAllCommissions(pageable);
        return ResponseEntity.ok(ApiResponse.success("Commissions retrieved successfully", PageResponse.from(commissionPage)));
    }

    @GetMapping("/pending-payment/{agentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PendingPaymentSummary>> getPendingPaymentSummary(
            @PathVariable UUID agentId) {
        PendingPaymentSummary response = queryService.getPendingPaymentSummary(agentId);
        return ResponseEntity.ok(ApiResponse.success("Pending payment summary retrieved successfully", response));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<ApiResponse<List<CommissionResponse>>> getMyCommissions() {
        UUID agentId = getCurrentAgentId();
        List<CommissionResponse> response = queryService.getCommissionsByAgent(agentId);
        return ResponseEntity.ok(ApiResponse.success("Commissions retrieved successfully", response));
    }

    @GetMapping("/my/pending-payment")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<ApiResponse<PendingPaymentSummary>> getMyPendingPayment() {
        UUID agentId = getCurrentAgentId();
        PendingPaymentSummary response = queryService.getPendingPaymentSummary(agentId);
        return ResponseEntity.ok(ApiResponse.success("Pending payment summary retrieved successfully", response));
    }

    @PostMapping("/pay-batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<BatchPaymentResponse>> payBatch(
            @Valid @RequestBody BatchPaymentRequest request) {
        BatchPaymentResponse response = lifecycleService.payBatch(request.getCommissionIds());
        return ResponseEntity.ok(ApiResponse.success(
                "Batch payment of LKR " + response.getTotalPaid() + " completed. "
                + response.getCommissionsPaid() + " commission(s) marked as paid.",
                response));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<CommissionResponse>>> getCommissionsByStatus(
            @PathVariable String status) {
        List<CommissionResponse> response = queryService.getCommissionsByStatus(
                com.ebee.commission.enums.CommissionStatus.valueOf(status.toUpperCase())
        );
        return ResponseEntity.ok(ApiResponse.success("Commissions retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CommissionResponse>> getCommissionById(@PathVariable UUID id) {
        CommissionResponse response = queryService.getCommissionById(id);
        return ResponseEntity.ok(ApiResponse.success("Commission retrieved successfully", response));
    }

    private UUID getCurrentAgentId() {
        UUID userId = securityService.getCurrentUserId();
        return agentService.getAgentByUserId(userId).getId();
    }
}
