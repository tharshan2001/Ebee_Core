package com.ebee.reminder.service;

import com.ebee.alert.enums.AlertType;
import com.ebee.notification.NotificationService;
import com.ebee.notification.model.EmailRequest;
import com.ebee.notification.model.SmsRequest;
import com.ebee.reminder.entity.Reminder;
import com.ebee.reminder.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderSchedulerService {

    private final ReminderRepository reminderRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void processReminders() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        processExactAlerts(today, now);
        processEarlyAlerts(today, now);
    }

    private void processExactAlerts(LocalDate date, LocalTime now) {
        List<Reminder> reminders = reminderRepository.findPendingRemindersForAlert(date, now);

        for (Reminder reminder : reminders) {
            try {
                String subject = "Reminder: " + reminder.getTitle();
                String message = "Your reminder \"" + reminder.getTitle() + "\" is due now."
                        + (reminder.getDescription() != null ? "\n\n" + reminder.getDescription() : "")
                        + "\n\nPriority: " + reminder.getPriority();

                if (Boolean.TRUE.equals(reminder.getSendEmail())) {
                    sendEmail(reminder, subject, message);
                }
                if (Boolean.TRUE.equals(reminder.getSendSms())) {
                    sendSms(reminder, message);
                }

                notificationService.createInternalAlert(reminder.getUser(), subject, message,
                        AlertType.REMINDER_DUE, reminder.getId(), "reminder");

                reminder.setAlertSent(true);
                reminder.setNotifiedAt(LocalDateTime.now());
                reminderRepository.save(reminder);

                log.info("Exact alert sent for reminder: {}", reminder.getId());
            } catch (Exception e) {
                log.error("Failed to send exact alert for reminder {}: {}", reminder.getId(), e.getMessage());
            }
        }
    }

    private void processEarlyAlerts(LocalDate date, LocalTime now) {
        LocalTime earlyBound = now.plusMinutes(5);
        List<Reminder> reminders = reminderRepository.findPendingRemindersForEarlyAlert(date, now, earlyBound);

        for (Reminder reminder : reminders) {
            try {
                String subject = "Upcoming Reminder: " + reminder.getTitle() + " in 5 minutes";
                String message = "Reminder \"" + reminder.getTitle() + "\" is coming up in 5 minutes."
                        + (reminder.getDescription() != null ? "\n\n" + reminder.getDescription() : "")
                        + "\n\nTime: " + reminder.getReminderTime()
                        + "\nPriority: " + reminder.getPriority();

                if (Boolean.TRUE.equals(reminder.getSendEmail())) {
                    sendEmail(reminder, subject, message);
                }
                if (Boolean.TRUE.equals(reminder.getSendSms())) {
                    sendSms(reminder, "Ebee: \"" + reminder.getTitle() + "\" in 5 minutes"
                            + (reminder.getReminderTime() != null ? " at " + reminder.getReminderTime() : ""));
                }

                notificationService.createInternalAlert(reminder.getUser(), subject, message,
                        AlertType.REMINDER_DUE, reminder.getId(), "reminder");

                reminder.setEarlyAlertSent(true);
                reminderRepository.save(reminder);

                log.info("Early alert sent for reminder: {}", reminder.getId());
            } catch (Exception e) {
                log.error("Failed to send early alert for reminder {}: {}", reminder.getId(), e.getMessage());
            }
        }
    }

    private void sendEmail(Reminder reminder, String subject, String message) {
        notificationService.sendEmail(EmailRequest.builder()
                .to(reminder.getUser().getEmail())
                .subject(subject)
                .body(message)
                .html(false)
                .build());
    }

    private void sendSms(Reminder reminder, String message) {
        if (reminder.getUser().getPhoneNumber() != null) {
            notificationService.sendSms(SmsRequest.builder()
                    .phoneNumbers(List.of(reminder.getUser().getPhoneNumber()))
                    .message(message)
                    .build());
        }
    }
}
