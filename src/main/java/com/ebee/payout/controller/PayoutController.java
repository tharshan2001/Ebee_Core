package com.ebee.payout.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.payout.dto.PayoutRequest;
import com.ebee.payout.dto.PayoutResponse;
import com.ebee.payout.service.PayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Payout statements. Admin only, except the PDF, which an agent may also download for their own
 * payouts (the service checks ownership). These had no role checks, so any signed-in user could
 * list every payout, create statements and mark them processed.
 */
@RestController
@RequestMapping("/api/payouts")
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutService payoutService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<PayoutResponse>>> getAllPayouts() {
        List<PayoutResponse> response = payoutService.getAllPayouts();
        return ResponseEntity.ok(ApiResponse.success("Payouts retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PayoutResponse>> getPayoutById(@PathVariable UUID id) {
        PayoutResponse response = payoutService.getPayoutById(id);
        return ResponseEntity.ok(ApiResponse.success("Payout retrieved successfully", response));
    }

    @GetMapping("/agent/{agentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<PayoutResponse>>> getPayoutsByAgent(
            @PathVariable UUID agentId) {
        List<PayoutResponse> response = payoutService.getPayoutsByAgentId(agentId);
        return ResponseEntity.ok(ApiResponse.success("Payouts retrieved successfully", response));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<PayoutResponse>>> createBatchPayouts(
            @Valid @RequestBody PayoutRequest request) {
        List<PayoutResponse> response = payoutService.createBatchPayouts(request.getAgentIds());
        return ResponseEntity.ok(ApiResponse.success("Batch payouts created successfully", response));
    }

    @PutMapping("/{id}/process")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PayoutResponse>> processPayout(@PathVariable UUID id) {
        PayoutResponse response = payoutService.processPayout(id);
        return ResponseEntity.ok(ApiResponse.success("Payout processed successfully", response));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'AGENT')")
    public ResponseEntity<Resource> getPayoutPdf(@PathVariable UUID id) {
        byte[] pdfBytes = payoutService.getPayoutPdf(id);
        ByteArrayResource resource = new ByteArrayResource(pdfBytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payout-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
}
