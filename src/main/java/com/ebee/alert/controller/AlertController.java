package com.ebee.alert.controller;

import com.ebee.alert.entity.Alert;
import com.ebee.alert.repository.AlertRepository;
import com.ebee.common.response.ApiResponse;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
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

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Alert>>> getAlerts(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 200);
        UUID userId = getUserId(authentication);
        Page<Alert> alerts = alertRepository.findByRecipientIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success("Alerts retrieved successfully", alerts));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(Authentication authentication) {
        UUID userId = getUserId(authentication);
        long count = alertRepository.countByRecipientIdAndIsReadFalse(userId);
        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved", Map.of("count", count)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable UUID id, Authentication authentication) {
        UUID userId = getUserId(authentication);
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + id));
        if (!alert.getRecipient().getId().equals(userId)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied"));
        }
        alert.setIsRead(true);
        alertRepository.save(alert);
        return ResponseEntity.ok(ApiResponse.success("Alert marked as read"));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(Authentication authentication) {
        UUID userId = getUserId(authentication);
        Page<Alert> unread = alertRepository.findByRecipientIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, Integer.MAX_VALUE));
        unread.getContent().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsRead()))
                .forEach(a -> {
                    a.setIsRead(true);
                    alertRepository.save(a);
                });
        return ResponseEntity.ok(ApiResponse.success("All alerts marked as read"));
    }

    private UUID getUserId(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }
}
