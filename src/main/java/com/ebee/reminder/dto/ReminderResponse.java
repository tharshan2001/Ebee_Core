package com.ebee.reminder.dto;

import com.ebee.reminder.enums.ReminderPriority;
import com.ebee.reminder.enums.ReminderStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class ReminderResponse {

    private UUID id;
    private UUID userId;
    private String title;
    private String description;
    private LocalDate reminderDate;
    private LocalTime reminderTime;
    private ReminderStatus status;
    private ReminderPriority priority;
    private String category;
    private Boolean sendEmail;
    private Boolean sendSms;
    private LocalDateTime notifiedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
