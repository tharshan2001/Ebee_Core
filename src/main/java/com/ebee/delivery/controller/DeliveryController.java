package com.ebee.delivery.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.delivery.dto.DeliveryResponse;
import com.ebee.delivery.dto.DeliveryStatusUpdateRequest;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
            @RequestParam(required = false) String district,
            @RequestParam(required = false) UUID tripId) {
        List<DeliveryResponse> responses = deliveryService.getDeliveries(status, district, tripId);
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
        var result = deliveryService.getDeliveryDetail(orderId);
        if (!result.isSuccess()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(result.getMessage()));
        }
        return ResponseEntity.ok(ApiResponse.success("Delivery retrieved successfully", result.getData()));
    }
}
