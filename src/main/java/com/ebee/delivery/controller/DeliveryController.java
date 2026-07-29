package com.ebee.delivery.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.delivery.dto.DeliveryResponse;
import com.ebee.delivery.dto.DeliveryStatusUpdateRequest;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<DeliveryResponse>> updateDeliveryStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody DeliveryStatusUpdateRequest request) {
        DeliveryResponse response = deliveryService.updateDeliveryStatus(orderId, request);
        return ResponseEntity.ok(ApiResponse.success("Delivery status updated successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<List<DeliveryResponse>>> getDeliveries(
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) String district) {
        List<DeliveryResponse> responses = deliveryService.getDeliveries(status, district);
        return ResponseEntity.ok(ApiResponse.success("Deliveries retrieved successfully", responses));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<List<DeliveryResponse>>> getMyDeliveries() {
        List<DeliveryResponse> responses = deliveryService.getMyDeliveries();
        return ResponseEntity.ok(ApiResponse.success("My deliveries retrieved successfully", responses));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<DeliveryResponse>> getDeliveryDetail(@PathVariable UUID orderId) {
        DeliveryResponse response = deliveryService.getDeliveryDetail(orderId);
        return ResponseEntity.ok(ApiResponse.success("Delivery retrieved successfully", response));
    }
}
