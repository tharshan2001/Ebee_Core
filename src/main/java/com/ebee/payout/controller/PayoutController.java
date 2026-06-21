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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payouts")
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutService payoutService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PayoutResponse>>> getAllPayouts() {
        List<PayoutResponse> response = payoutService.getAllPayouts();
        return ResponseEntity.ok(ApiResponse.success("Payouts retrieved successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PayoutResponse>> getPayoutById(@PathVariable UUID id) {
        PayoutResponse response = payoutService.getPayoutById(id);
        return ResponseEntity.ok(ApiResponse.success("Payout retrieved successfully", response));
    }

    @GetMapping("/agent/{agentId}")
    public ResponseEntity<ApiResponse<List<PayoutResponse>>> getPayoutsByAgent(
            @PathVariable UUID agentId) {
        List<PayoutResponse> response = payoutService.getPayoutsByAgentId(agentId);
        return ResponseEntity.ok(ApiResponse.success("Payouts retrieved successfully", response));
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<PayoutResponse>>> createBatchPayouts(
            @Valid @RequestBody PayoutRequest request) {
        List<PayoutResponse> response = payoutService.createBatchPayouts(request.getAgentIds());
        return ResponseEntity.ok(ApiResponse.success("Batch payouts created successfully", response));
    }

    @PutMapping("/{id}/process")
    public ResponseEntity<ApiResponse<PayoutResponse>> processPayout(@PathVariable UUID id) {
        PayoutResponse response = payoutService.processPayout(id);
        return ResponseEntity.ok(ApiResponse.success("Payout processed successfully", response));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<Resource> getPayoutPdf(@PathVariable UUID id) {
        byte[] pdfBytes = payoutService.getPayoutPdf(id);
        ByteArrayResource resource = new ByteArrayResource(pdfBytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payout-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
}
