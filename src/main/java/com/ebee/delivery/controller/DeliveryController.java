package com.ebee.delivery.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.delivery.dto.AssignDriverRequest;
import com.ebee.delivery.dto.DeliveryStatusUpdateRequest;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.service.DeliveryService;
import com.ebee.order.dto.OrderResponse;
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

    @PatchMapping("/{orderId}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> assignDelivery(
            @PathVariable UUID orderId,
            @Valid @RequestBody AssignDriverRequest request) {
        OrderResponse response = deliveryService.assignDelivery(orderId, request.getDriverId());
        return ResponseEntity.ok(ApiResponse.success("Delivery assigned successfully", response));
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateDeliveryStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody DeliveryStatusUpdateRequest request) {
        OrderResponse response = deliveryService.updateDeliveryStatus(orderId, request);
        return ResponseEntity.ok(ApiResponse.success("Delivery status updated successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getDeliveries(
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) UUID tripId) {
        List<OrderResponse> responses = deliveryService.getDeliveries(status, district, tripId);
        return ResponseEntity.ok(ApiResponse.success("Deliveries retrieved successfully", responses));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyDeliveries() {
        List<OrderResponse> responses = deliveryService.getMyDeliveries();
        return ResponseEntity.ok(ApiResponse.success("My deliveries retrieved successfully", responses));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<OrderResponse>> getDeliveryDetail(@PathVariable UUID orderId) {
        OrderResponse response = deliveryService.getDeliveryDetail(orderId);
        return ResponseEntity.ok(ApiResponse.success("Delivery retrieved successfully", response));
    }
}
