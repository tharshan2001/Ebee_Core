package com.ebee.reminder.entity;

import com.ebee.reminder.enums.ReminderPriority;
import com.ebee.reminder.enums.ReminderStatus;
import com.ebee.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "reminders")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate;

    @Column(name = "reminder_time")
    private LocalTime reminderTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReminderStatus status = ReminderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReminderPriority priority = ReminderPriority.MEDIUM;

    private String category;

    @Column(name = "send_email")
    private Boolean sendEmail = false;

    @Column(name = "send_sms")
    private Boolean sendSms = false;

    @Column(name = "early_alert_sent")
    private Boolean earlyAlertSent = false;

    @Column(name = "alert_sent")
    private Boolean alertSent = false;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = ReminderStatus.PENDING;
        if (priority == null) priority = ReminderPriority.MEDIUM;
        if (sendEmail == null) sendEmail = false;
        if (sendSms == null) sendSms = false;
        if (earlyAlertSent == null) earlyAlertSent = false;
        if (alertSent == null) alertSent = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
