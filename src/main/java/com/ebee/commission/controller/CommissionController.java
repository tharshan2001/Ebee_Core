package com.ebee.commission.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.commission.dto.CommissionCalculateRequest;
import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.service.CommissionService;
import lombok.RequiredArgsConstructor;
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

    private final CommissionService commissionService;

    @PostMapping("/calculate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionResponse>> calculateCommission(
            @RequestBody CommissionCalculateRequest request) {
        CommissionResponse response = commissionService.calculateCommission(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Commission calculated successfully", response));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionResponse>> approveCommission(@PathVariable UUID id) {
        CommissionResponse response = commissionService.approveCommission(id);
        return ResponseEntity.ok(ApiResponse.success("Commission approved successfully", response));
    }

    @PutMapping("/{id}/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionResponse>> markAsProcessing(@PathVariable UUID id) {
        CommissionResponse response = commissionService.markAsProcessing(id);
        return ResponseEntity.ok(ApiResponse.success("Commission marked as processing", response));
    }

    @PutMapping("/{id}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionResponse>> markAsPaid(@PathVariable UUID id) {
        CommissionResponse response = commissionService.markAsPaid(id);
        return ResponseEntity.ok(ApiResponse.success("Commission marked as paid", response));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionResponse>> rejectCommission(
            @PathVariable UUID id,
            @RequestParam String reason) {
        CommissionResponse response = commissionService.rejectCommission(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Commission rejected", response));
    }

    @GetMapping("/agent/{agentId}")
    public ResponseEntity<ApiResponse<List<CommissionResponse>>> getCommissionsByAgent(
            @PathVariable UUID agentId) {
        List<CommissionResponse> response = commissionService.getCommissionsByAgent(agentId);
        return ResponseEntity.ok(ApiResponse.success("Commissions retrieved successfully", response));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CommissionResponse>>> getCommissionsByStatus(
            @PathVariable String status) {
        List<CommissionResponse> response = commissionService.getCommissionsByStatus(
                com.ebee.commission.enums.CommissionStatus.valueOf(status.toUpperCase())
        );
        return ResponseEntity.ok(ApiResponse.success("Commissions retrieved successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CommissionResponse>> getCommissionById(@PathVariable UUID id) {
        CommissionResponse response = commissionService.getCommissionById(id);
        return ResponseEntity.ok(ApiResponse.success("Commission retrieved successfully", response));
    }
}
