package com.ebee.delivery.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.delivery.dto.AssignDriverRequest;
import com.ebee.delivery.dto.TripRequest;
import com.ebee.delivery.dto.TripResponse;
import com.ebee.delivery.enums.TripStatus;
import com.ebee.delivery.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/delivery-trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TripResponse>> createTrip(@Valid @RequestBody TripRequest request) {
        TripResponse response = tripService.createTrip(request);
        return ResponseEntity.ok(ApiResponse.success("Trip created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<List<TripResponse>>> getTrips(
            @RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) String district) {
        List<TripResponse> responses = tripService.getTrips(status, district);
        return ResponseEntity.ok(ApiResponse.success("Trips retrieved successfully", responses));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<TripResponse>> getTripDetail(@PathVariable UUID id) {
        TripResponse response = tripService.getTripDetail(id);
        return ResponseEntity.ok(ApiResponse.success("Trip retrieved successfully", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<TripResponse>> updateTripStatus(
            @PathVariable UUID id,
            @RequestParam TripStatus status) {
        TripResponse response = tripService.updateTripStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Trip status updated successfully", response));
    }

    @PatchMapping("/{id}/driver")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TripResponse>> assignDriver(
            @PathVariable UUID id,
            @Valid @RequestBody AssignDriverRequest request) {
        TripResponse response = tripService.assignDriver(id, request.getDriverId());
        return ResponseEntity.ok(ApiResponse.success("Driver assigned successfully", response));
    }

    @PostMapping("/{tripId}/orders/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TripResponse>> assignOrderToTrip(
            @PathVariable UUID tripId,
            @PathVariable UUID orderId) {
        TripResponse response = tripService.assignOrderToTrip(tripId, orderId);
        return ResponseEntity.ok(ApiResponse.success("Order assigned to trip successfully", response));
    }

    @DeleteMapping("/{tripId}/orders/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TripResponse>> removeOrderFromTrip(
            @PathVariable UUID tripId,
            @PathVariable UUID orderId) {
        TripResponse response = tripService.removeOrderFromTrip(tripId, orderId);
        return ResponseEntity.ok(ApiResponse.success("Order removed from trip successfully", response));
    }
}
