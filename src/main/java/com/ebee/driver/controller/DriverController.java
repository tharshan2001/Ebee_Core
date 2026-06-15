package com.ebee.driver.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.driver.dto.AvailabilityRequest;
import com.ebee.driver.dto.DriverRequest;
import com.ebee.driver.dto.DriverResponse;
import com.ebee.driver.dto.DriverWithCredentialsResponse;
import com.ebee.driver.enums.DriverAvailability;
import com.ebee.driver.service.DriverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DriverWithCredentialsResponse>> createDriver(@Valid @RequestBody DriverRequest request) {
        DriverWithCredentialsResponse response = driverService.createDriver(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Driver created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<Page<DriverResponse>>> getDrivers(
            @RequestParam(required = false) DriverAvailability availability,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Sort.Direction direction = sort.endsWith(",asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = sort.replace(",asc", "").replace(",desc", "");
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
        Page<DriverResponse> responses = driverService.getDrivers(availability, pageable);
        return ResponseEntity.ok(ApiResponse.success("Drivers retrieved successfully", responses));
    }

    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<Page<DriverResponse>>> getAvailableDrivers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.ASC, "user.firstName"));
        Page<DriverResponse> responses = driverService.getDrivers(DriverAvailability.AVAILABLE, pageable);
        return ResponseEntity.ok(ApiResponse.success("Available drivers retrieved successfully", responses));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<DriverResponse>> getDriver(@PathVariable UUID id) {
        DriverResponse response = driverService.getDriver(id);
        return ResponseEntity.ok(ApiResponse.success("Driver retrieved successfully", response));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DriverResponse>> updateDriver(
            @PathVariable UUID id,
            @Valid @RequestBody DriverRequest request) {
        DriverResponse response = driverService.updateDriver(id, request);
        return ResponseEntity.ok(ApiResponse.success("Driver updated successfully", response));
    }

    @PatchMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<DriverResponse>> updateAvailability(
            @PathVariable UUID id,
            @Valid @RequestBody AvailabilityRequest request) {
        DriverResponse response = driverService.updateAvailability(id, request);
        return ResponseEntity.ok(ApiResponse.success("Availability updated successfully", response));
    }
}
