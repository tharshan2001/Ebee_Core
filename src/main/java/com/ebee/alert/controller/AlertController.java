package com.ebee.alert.controller;

import com.ebee.alert.entity.Alert;
import com.ebee.alert.service.AlertService;
import com.ebee.common.response.ApiResponse;
import com.ebee.common.security.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AlertController {

    private final AlertService alertService;
    private final SecurityService securityService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Alert>>> getAlerts(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 200);
        UUID userId = securityService.getCurrentUserId(authentication);
        Page<Alert> alerts = alertService.getAlerts(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success("Alerts retrieved successfully", alerts));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(Authentication authentication) {
        UUID userId = securityService.getCurrentUserId(authentication);
        long count = alertService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved", Map.of("count", count)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable UUID id, Authentication authentication) {
        UUID userId = securityService.getCurrentUserId(authentication);
        var result = alertService.markAsRead(id, userId);
        if (!result.isSuccess()) {
            return ResponseEntity.status(403).body(ApiResponse.error(result.getMessage()));
        }
        return ResponseEntity.ok(ApiResponse.success("Alert marked as read"));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(Authentication authentication) {
        UUID userId = securityService.getCurrentUserId(authentication);
        alertService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success("All alerts marked as read"));
    }
}
