package com.ebee.driver.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.driver.dto.DriverLocationHistoryResponse;
import com.ebee.driver.dto.DriverLocationResponse;
import com.ebee.driver.service.DriverLocationService;
import com.ebee.notification.NotificationPort;
import com.ebee.notification.model.SmsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/driver-locations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class DriverLocationAdminController {

    private final DriverLocationService driverLocationService;
    private final NotificationPort notificationPort;

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<DriverLocationHistoryResponse>>> getActiveLocations() {
        List<DriverLocationHistoryResponse> locations = driverLocationService.getActiveLocations();
        return ResponseEntity.ok(ApiResponse.success("Active driver locations retrieved", locations));
    }

    @GetMapping("/{driverId}")
    public ResponseEntity<ApiResponse<DriverLocationHistoryResponse>> getDriverLocation(
            @PathVariable UUID driverId) {
        return driverLocationService.getLocationHistory(driverId)
                .map(loc -> ResponseEntity.ok(ApiResponse.success("Location retrieved", loc)))
                .orElse(ResponseEntity.ok(ApiResponse.success("No location data available")));
    }

    @GetMapping("/{driverId}/history")
    public ResponseEntity<ApiResponse<DriverLocationHistoryResponse>> getDriverLocationHistory(
            @PathVariable UUID driverId) {
        return driverLocationService.getLocationHistory(driverId)
                .map(loc -> ResponseEntity.ok(ApiResponse.success("Location history retrieved", loc)))
                .orElse(ResponseEntity.ok(ApiResponse.success("No location history available")));
    }

    @PostMapping("/test-sms")
    public ResponseEntity<ApiResponse<Void>> testSms(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("phone is required"));
        }
        notificationPort.sendSms(SmsRequest.builder()
                .phoneNumbers(List.of(phone))
                .message("Ebee: This is a test SMS from the platform.")
                .build());
        return ResponseEntity.ok(ApiResponse.success("SMS sent successfully"));
    }
}
