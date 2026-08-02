package com.ebee.order.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.common.security.SecurityService;
import com.ebee.order.dto.InitiateReturnExchangeRequest;
import com.ebee.order.dto.ReturnExchangeResponse;
import com.ebee.order.dto.ReviewReturnExchangeRequest;
import com.ebee.order.service.ReturnExchangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class ReturnExchangeController {

    private final ReturnExchangeService returnExchangeService;
    private final SecurityService securityService;

    @PostMapping("/{orderId}/return-exchange")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ReturnExchangeResponse>> initiateReturnExchange(
            @PathVariable UUID orderId,
            @Valid @RequestBody InitiateReturnExchangeRequest request) {
        ReturnExchangeResponse response = returnExchangeService.initiateReturnExchange(orderId, request);
        return ResponseEntity.ok(ApiResponse.success("Return exchange initiated successfully", response));
    }

    @GetMapping("/return-exchange/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ReturnExchangeResponse>>> getPendingReviews() {
        List<ReturnExchangeResponse> pending = returnExchangeService.getPendingReviews();
        return ResponseEntity.ok(ApiResponse.success("Pending return exchanges retrieved", pending));
    }

    @GetMapping("/{orderId}/return-exchange")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ReturnExchangeResponse>> getReturnExchangeByOrder(@PathVariable UUID orderId) {
        ReturnExchangeResponse response = returnExchangeService.getByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.success("Return exchange retrieved", response));
    }

    @PostMapping("/{returnExchangeId}/return-exchange/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ReturnExchangeResponse>> reviewReturnExchange(
            @PathVariable UUID returnExchangeId,
            @Valid @RequestBody ReviewReturnExchangeRequest request) {
        UUID reviewedBy = securityService.getCurrentUserId();
        ReturnExchangeResponse response = returnExchangeService.reviewReturnExchange(returnExchangeId, request, reviewedBy);
        return ResponseEntity.ok(ApiResponse.success("Return exchange reviewed successfully", response));
    }

    @GetMapping("/return-exchange/count")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Long>> getPendingCount() {
        long count = returnExchangeService.countPending();
        return ResponseEntity.ok(ApiResponse.success("Pending count retrieved", count));
    }
}
