package com.ebee.driver.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.common.response.PageResponse;
import com.ebee.common.util.PaginationUtils;
import com.ebee.common.util.SortUtils;
import com.ebee.common.util.SortableFields;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.driver.dto.*;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.enums.DriverAvailability;
import com.ebee.driver.service.DriverAppService;
import com.ebee.driver.service.DriverLocationService;
import com.ebee.expense.dto.ExpenseRequest;
import com.ebee.expense.dto.ExpenseResponse;
import com.ebee.expense.enums.ExpenseStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ebee.trip.dto.AddOrdersToTripRequest;
import com.ebee.trip.dto.TripResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/driver")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
public class DriverAppController {

    private final DriverAppService driverAppService;
    private final com.ebee.driver.service.DriverDeliveryService driverDeliveryService;
    private final com.ebee.driver.service.DriverTripService driverTripService;
    private final DriverLocationService driverLocationService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DriverDashboardResponse>> getDashboard() {
        DriverDashboardResponse response = driverAppService.getDashboard();
        return ResponseEntity.ok(ApiResponse.success("Dashboard retrieved successfully", response));
    }

    @GetMapping("/deliveries")
    public ResponseEntity<ApiResponse<PageResponse<DriverDeliveryResponse>>> getMyDeliveries(
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {
        int safeSize = PaginationUtils.safeSize(size);
        Pageable pageable = PageRequest.of(PaginationUtils.safePage(page), safeSize,
                SortUtils.parse(sort, SortableFields.AllowedFor.DRIVER_DELIVERY));
        Page<DriverDeliveryResponse> responses = driverDeliveryService.getMyDeliveries(status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Deliveries retrieved successfully", PageResponse.from(responses)));
    }

    @GetMapping("/deliveries/{orderId}")
    public ResponseEntity<ApiResponse<DriverDeliveryResponse>> getDeliveryDetail(@PathVariable UUID orderId) {
        DriverDeliveryResponse response = driverDeliveryService.getDeliveryDetail(orderId);
        return ResponseEntity.ok(ApiResponse.success("Delivery retrieved successfully", response));
    }

    @PatchMapping("/deliveries/{orderId}/status")
    public ResponseEntity<ApiResponse<DriverDeliveryResponse>> updateDeliveryStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody DriverDeliveryStatusRequest request) {
        DriverDeliveryResponse response = driverDeliveryService.updateDeliveryStatus(orderId, request);
        return ResponseEntity.ok(ApiResponse.success("Delivery status updated successfully", response));
    }

    @PostMapping(value = "/deliveries/{orderId}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DriverDeliveryResponse>> completeDelivery(
            @PathVariable UUID orderId,
            @RequestParam(value = "photo", required = false) MultipartFile photo,
            @RequestParam(value = "signature", required = false) MultipartFile signature,
            @RequestParam(value = "driverNotes", required = false) String driverNotes) {
        DriverDeliveryResponse response = driverDeliveryService.completeDelivery(orderId, photo, signature, driverNotes);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Delivery completed successfully", response));
    }

    @PatchMapping("/availability")
    public ResponseEntity<ApiResponse<com.ebee.driver.dto.DriverResponse>> updateAvailability(
            @RequestBody Map<String, Object> body) {
        Object availValue = body.get("availability");
        DriverAvailability availability;
        if (availValue instanceof Boolean) {
            availability = Boolean.TRUE.equals(availValue) ? DriverAvailability.AVAILABLE : DriverAvailability.OFFLINE;
        } else if (availValue instanceof String s) {
            availability = DriverAvailability.valueOf(s);
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("availability must be a boolean or string"));
        }
        com.ebee.driver.dto.DriverResponse response = driverAppService.updateMyAvailability(availability);
        return ResponseEntity.ok(ApiResponse.success("Availability updated successfully", response));
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<DriverProfileResponse>> getProfile() {
        DriverProfileResponse response = driverAppService.getMyProfile();
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully", response));
    }

    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<DriverProfileResponse>> updateProfile(
            @Valid @RequestBody DriverProfileRequest request) {
        DriverProfileResponse response = driverAppService.updateMyProfile(request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DriverStatsResponse>> getStats() {
        DriverStatsResponse response = driverAppService.getMyStats();
        return ResponseEntity.ok(ApiResponse.success("Stats retrieved successfully", response));
    }

    @PostMapping("/expenses")
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(
            @RequestPart("expense") @Valid ExpenseRequest request,
            @RequestParam(value = "receipt", required = false) MultipartFile receipt) {
        ExpenseResponse response = driverAppService.createExpense(request, receipt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Expense created successfully", response));
    }

    @GetMapping("/expenses")
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getMyExpenses(
            @RequestParam(required = false) ExpenseStatus status) {
        List<ExpenseResponse> responses = driverAppService.getMyExpenses(status);
        return ResponseEntity.ok(ApiResponse.success("Expenses retrieved successfully", responses));
    }

    @GetMapping("/trips/available")
    public ResponseEntity<ApiResponse<List<TripResponse>>> getAvailableTrips() {
        List<TripResponse> trips = driverTripService.getAvailableTrips();
        return ResponseEntity.ok(ApiResponse.success("Available trips retrieved successfully", trips));
    }

    @GetMapping("/trips/my")
    public ResponseEntity<ApiResponse<TripResponse>> getMyInProgressTrip() {
        TripResponse trip = driverTripService.getMyInProgressTrip();
        return ResponseEntity.ok(ApiResponse.success("Current trip retrieved successfully", trip));
    }

    @GetMapping("/trips/{id}")
    public ResponseEntity<ApiResponse<TripResponse>> getTripById(@PathVariable UUID id) {
        TripResponse response = driverTripService.getTripById(id);
        return ResponseEntity.ok(ApiResponse.success("Trip retrieved successfully", response));
    }

    @PatchMapping("/trips/{id}/start")
    public ResponseEntity<ApiResponse<TripResponse>> startTrip(@PathVariable UUID id) {
        TripResponse response = driverTripService.startTrip(id);
        return ResponseEntity.ok(ApiResponse.success("Trip started successfully", response));
    }

    @PatchMapping("/trips/{id}/orders")
    public ResponseEntity<ApiResponse<TripResponse>> addOrdersToTrip(
            @PathVariable UUID id,
            @Valid @RequestBody AddOrdersToTripRequest request) {
        TripResponse response = driverTripService.addOrdersToTrip(id, request);
        return ResponseEntity.ok(ApiResponse.success("Orders updated successfully", response));
    }

    @PatchMapping("/trips/{id}/mileage")
    public ResponseEntity<ApiResponse<TripResponse>> updateMileage(
            @PathVariable UUID id,
            @RequestParam BigDecimal mileageKm) {
        TripResponse response = driverTripService.updateMileage(id, mileageKm);
        return ResponseEntity.ok(ApiResponse.success("Mileage updated successfully", response));
    }

    @PatchMapping("/trips/{id}/complete")
    public ResponseEntity<ApiResponse<TripResponse>> completeTrip(@PathVariable UUID id) {
        TripResponse response = driverTripService.completeTrip(id);
        return ResponseEntity.ok(ApiResponse.success("Trip completed successfully", response));
    }

    @PostMapping(value = "/trips/{id}/expenses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ExpenseResponse>> createTripExpense(
            @PathVariable UUID id,
            @RequestPart("expense") @Valid ExpenseRequest request,
            @RequestParam(value = "receipt", required = false) MultipartFile receipt) {
        ExpenseResponse response = driverTripService.createTripExpense(id, request, receipt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Expense created successfully", response));
    }

    @PostMapping(value = "/profile/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DriverProfileResponse>> uploadProfilePhoto(
            @RequestParam("photo") MultipartFile photo) {
        DriverProfileResponse response = driverAppService.uploadProfilePhoto(photo);
        return ResponseEntity.ok(ApiResponse.success("Profile photo updated successfully", response));
    }

    @PostMapping("/device-token")
    public ResponseEntity<ApiResponse<Void>> registerDeviceToken(
            @Valid @RequestBody Map<String, String> body) {
        String fcmToken = body.get("fcmToken");
        if (fcmToken == null || fcmToken.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("fcmToken is required"));
        }
        driverAppService.registerDeviceToken(fcmToken);
        return ResponseEntity.ok(ApiResponse.success("Device token registered successfully"));
    }

    @PostMapping("/location")
    public ResponseEntity<ApiResponse<DriverLocationResponse>> updateLocation(
            @Valid @RequestBody DriverLocationRequest request) {
        Driver driver = driverAppService.getCurrentDriver();
        String driverName = driverAppService.getCurrentDriverName();
        DriverLocationResponse response = driverLocationService.updateLocation(driver.getId(), driverName, request);
        return ResponseEntity.ok(ApiResponse.success("Location updated", response));
    }

    @GetMapping("/location")
    public ResponseEntity<ApiResponse<DriverLocationResponse>> getMyLocation() {
        Driver driver = driverAppService.getCurrentDriver();
        Optional<DriverLocationResponse> location = driverLocationService.getLocation(driver.getId());
        return location
                .map(loc -> ResponseEntity.ok(ApiResponse.success("Location retrieved", loc)))
                .orElse(ResponseEntity.ok(ApiResponse.success("No location data available")));
    }
}
