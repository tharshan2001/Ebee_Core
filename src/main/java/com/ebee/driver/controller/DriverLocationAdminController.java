package com.ebee.driver.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.driver.dto.DriverLocationResponse;
import com.ebee.driver.service.DriverLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/driver-locations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class DriverLocationAdminController {

    private final DriverLocationService driverLocationService;

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<DriverLocationResponse>>> getActiveLocations() {
        List<DriverLocationResponse> locations = driverLocationService.getActiveLocations();
        return ResponseEntity.ok(ApiResponse.success("Active driver locations retrieved", locations));
    }

    @GetMapping("/{driverId}")
    public ResponseEntity<ApiResponse<DriverLocationResponse>> getDriverLocation(
            @PathVariable UUID driverId) {
        return driverLocationService.getCurrentLocation(driverId)
                .map(loc -> ResponseEntity.ok(ApiResponse.success("Location retrieved", loc)))
                .orElse(ResponseEntity.ok(ApiResponse.success("No location data available")));
    }
}
