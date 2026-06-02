package com.ebee.commission.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.commission.dto.CommissionConfigRequest;
import com.ebee.commission.dto.CommissionConfigResponse;
import com.ebee.commission.enums.CommissionType;
import com.ebee.commission.service.CommissionConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/commission-configs")
@RequiredArgsConstructor
public class CommissionConfigController {

    private final CommissionConfigService configService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionConfigResponse>> createConfig(
            @RequestBody CommissionConfigRequest request) {
        CommissionConfigResponse response = configService.createConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Commission config created successfully", response));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionConfigResponse>> updateConfig(
            @PathVariable UUID id,
            @RequestBody CommissionConfigRequest request) {
        CommissionConfigResponse response = configService.updateConfig(id, request);
        return ResponseEntity.ok(ApiResponse.success("Commission config updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(@PathVariable UUID id) {
        configService.deleteConfig(id);
        return ResponseEntity.ok(ApiResponse.success("Commission config deactivated successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionConfigResponse>> getConfigById(
            @PathVariable UUID id) {
        CommissionConfigResponse response = configService.getConfigById(id);
        return ResponseEntity.ok(ApiResponse.success("Commission config retrieved successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CommissionConfigResponse>>> getAllConfigs() {
        List<CommissionConfigResponse> response = configService.getAllConfigs();
        return ResponseEntity.ok(ApiResponse.success("Commission configs retrieved successfully", response));
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CommissionConfigResponse>>> getActiveConfigs() {
        List<CommissionConfigResponse> response = configService.getActiveConfigs();
        return ResponseEntity.ok(ApiResponse.success("Active commission configs retrieved successfully", response));
    }

    @GetMapping("/type/{type}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CommissionConfigResponse>>> getConfigsByType(
            @PathVariable CommissionType type) {
        List<CommissionConfigResponse> response = configService.getConfigsByType(type);
        return ResponseEntity.ok(ApiResponse.success("Commission configs retrieved successfully", response));
    }

    @GetMapping("/type/{type}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CommissionConfigResponse>>> getActiveConfigsByType(
            @PathVariable CommissionType type) {
        List<CommissionConfigResponse> response = configService.getActiveConfigsByType(type);
        return ResponseEntity.ok(ApiResponse.success("Active commission configs retrieved successfully", response));
    }

    @GetMapping("/default")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionConfigResponse>> getDefaultConfig() {
        CommissionConfigResponse response = configService.getDefaultConfig();
        return ResponseEntity.ok(ApiResponse.success("Default commission config retrieved successfully", response));
    }

    @PutMapping("/default")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommissionConfigResponse>> updateDefaultConfig(
            @Valid @RequestBody CommissionConfigRequest request) {
        CommissionConfigResponse response = configService.updateDefaultConfig(request);
        return ResponseEntity.ok(ApiResponse.success("Default commission config updated successfully", response));
    }

    @GetMapping("/performance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CommissionConfigResponse>>> getPerformanceConfigs() {
        List<CommissionConfigResponse> response = configService.getPerformanceConfigs();
        return ResponseEntity.ok(ApiResponse.success("Performance commission configs retrieved successfully", response));
    }
}
