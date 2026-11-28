package com.ebee.trip.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.common.response.PageResponse;
import com.ebee.common.util.PaginationUtils;
import com.ebee.common.util.SortUtils;
import com.ebee.common.util.SortableFields;
import com.ebee.trip.dto.AddOrdersToTripRequest;
import com.ebee.trip.dto.CreateTripRequest;
import com.ebee.trip.dto.TripOrderMarkerDto;
import com.ebee.trip.dto.TripResponse;
import com.ebee.trip.dto.UpdateTripRequest;
import com.ebee.trip.enums.TripStatus;
import com.ebee.trip.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TripResponse>> createTrip(@Valid @RequestBody CreateTripRequest request) {
        TripResponse response = tripService.createTrip(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Trip created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<TripResponse>>> getAllTrips(
            @RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo) {
        List<TripResponse> trips = tripService.getAllTrips(status, driverId, dateFrom, dateTo);
        return ResponseEntity.ok(ApiResponse.success("Trips retrieved successfully", trips));
    }

    @GetMapping("/paginated")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<TripResponse>>> getTripsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String search) {
        Pageable pageable = PageRequest.of(
                PaginationUtils.safePage(page),
                PaginationUtils.safeSize(size),
                SortUtils.parse(sort, SortableFields.AllowedFor.TRIP));
        PageResponse<TripResponse> response = tripService.getAllTripsPaginated(
                status, driverId, dateFrom, dateTo, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Trips retrieved successfully", response));
    }

    @GetMapping("/status-counts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<java.util.Map<TripStatus, Long>>> getStatusCounts() {
        return ResponseEntity.ok(ApiResponse.success("Trip counts retrieved successfully", tripService.countByStatus()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<TripResponse>> getTripById(@PathVariable UUID id) {
        TripResponse response = tripService.getTripById(id);
        return ResponseEntity.ok(ApiResponse.success("Trip retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TripResponse>> updateTrip(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTripRequest request) {
        TripResponse response = tripService.updateTrip(id, request);
        return ResponseEntity.ok(ApiResponse.success("Trip updated successfully", response));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TripResponse>> cancelTrip(@PathVariable UUID id) {
        TripResponse response = tripService.cancelTrip(id);
        return ResponseEntity.ok(ApiResponse.success("Trip cancelled successfully", response));
    }

    @PatchMapping("/{id}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TripResponse>> startTrip(@PathVariable UUID id) {
        TripResponse response = tripService.startTrip(id);
        return ResponseEntity.ok(ApiResponse.success("Trip started successfully", response));
    }

    @PatchMapping("/{id}/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TripResponse>> addOrdersToTrip(
            @PathVariable UUID id,
            @Valid @RequestBody AddOrdersToTripRequest request) {
        TripResponse response = tripService.addOrdersToTrip(id, request);
        return ResponseEntity.ok(ApiResponse.success("Orders updated successfully", response));
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TripResponse>> completeTrip(@PathVariable UUID id) {
        TripResponse response = tripService.completeTrip(id);
        return ResponseEntity.ok(ApiResponse.success("Trip completed successfully", response));
    }

    @GetMapping("/{id}/order-markers")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<List<TripOrderMarkerDto>>> getTripOrderMarkers(@PathVariable UUID id) {
        List<TripOrderMarkerDto> markers = tripService.getTripOrderMarkers(id);
        return ResponseEntity.ok(ApiResponse.success("Trip order markers retrieved successfully", markers));
    }

    @GetMapping("/driver/{driverId}/active/order-markers")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<List<TripOrderMarkerDto>>> getActiveTripOrderMarkers(@PathVariable UUID driverId) {
        List<TripOrderMarkerDto> markers = tripService.getActiveTripOrderMarkers(driverId);
        return ResponseEntity.ok(ApiResponse.success("Active trip order markers retrieved successfully", markers));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTrip(@PathVariable UUID id) {
        tripService.deleteTrip(id);
        return ResponseEntity.ok(ApiResponse.success("Trip deleted successfully"));
    }
}
