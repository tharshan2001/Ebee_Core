package com.ebee.reminder.dto;

import com.ebee.reminder.enums.ReminderPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class ReminderRequest {

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private LocalDate reminderDate;

    private LocalTime reminderTime;

    private ReminderPriority priority;

    private String category;

    private Boolean sendEmail;

    private Boolean sendSms;
}
