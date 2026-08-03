package com.ebee.reminder.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.common.result.Result;
import com.ebee.common.security.SecurityService;
import com.ebee.reminder.dto.ReminderRequest;
import com.ebee.reminder.dto.ReminderResponse;
import com.ebee.reminder.service.ReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReminderController {

    private final ReminderService reminderService;
    private final SecurityService securityService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReminderResponse>>> getReminders(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        UUID userId = securityService.getCurrentUserId(authentication);

        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = startDate.plusMonths(1).minusDays(1);

        List<ReminderResponse> reminders = reminderService.getRemindersByDateRange(userId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Reminders retrieved successfully", reminders));
    }

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<ReminderResponse>>> getTodayReminders(Authentication authentication) {
        UUID userId = securityService.getCurrentUserId(authentication);
        List<ReminderResponse> reminders = reminderService.getTodayReminders(userId);
        return ResponseEntity.ok(ApiResponse.success("Today's reminders retrieved successfully", reminders));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReminderResponse>> getReminder(@PathVariable UUID id, Authentication authentication) {
        Result<ReminderResponse> result = reminderService.getReminderById(id);
        if (!result.isSuccess()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(result.getMessage()));
        }
        UUID currentUserId = securityService.getCurrentUserId(authentication);
        if (!result.getData().getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not have access to this reminder");
        }
        return ResponseEntity.ok(ApiResponse.success("Reminder retrieved successfully", result.getData()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReminderResponse>> createReminder(
            Authentication authentication,
            @Valid @RequestBody ReminderRequest request) {
        UUID userId = securityService.getCurrentUserId(authentication);
        ReminderResponse response = reminderService.createReminder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reminder created successfully", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ReminderResponse>> updateReminder(
            @PathVariable UUID id,
            @Valid @RequestBody ReminderRequest request,
            Authentication authentication) {
        Result<ReminderResponse> existingResult = reminderService.getReminderById(id);
        if (!existingResult.isSuccess()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(existingResult.getMessage()));
        }
        UUID currentUserId = securityService.getCurrentUserId(authentication);
        if (!existingResult.getData().getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not have access to this reminder");
        }
        ReminderResponse response = reminderService.updateReminder(id, request);
        return ResponseEntity.ok(ApiResponse.success("Reminder updated successfully", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReminder(@PathVariable UUID id, Authentication authentication) {
        Result<ReminderResponse> existingResult = reminderService.getReminderById(id);
        if (!existingResult.isSuccess()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        UUID currentUserId = securityService.getCurrentUserId(authentication);
        if (!existingResult.getData().getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not have access to this reminder");
        }
        reminderService.deleteReminder(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<ReminderResponse>> completeReminder(@PathVariable UUID id, Authentication authentication) {
        Result<ReminderResponse> existingResult = reminderService.getReminderById(id);
        if (!existingResult.isSuccess()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(existingResult.getMessage()));
        }
        UUID currentUserId = securityService.getCurrentUserId(authentication);
        if (!existingResult.getData().getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not have access to this reminder");
        }
        ReminderResponse response = reminderService.completeReminder(id);
        return ResponseEntity.ok(ApiResponse.success("Reminder completed successfully", response));
    }
}
