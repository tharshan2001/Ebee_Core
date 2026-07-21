package com.ebee.reminder.service;

import com.ebee.alert.enums.AlertType;
import com.ebee.notification.NotificationPort;
import com.ebee.reminder.entity.Reminder;
import com.ebee.reminder.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderSchedulerService {

    private final ReminderRepository reminderRepository;
    private final NotificationPort notificationPort;

    @Scheduled(fixedRate = 60_000)
    @SchedulerLock(name = "processReminders", lockAtMostFor = "PT2M", lockAtLeastFor = "PT55S")
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
                Map<String, String> p = new HashMap<>();
                p.put("title", reminder.getTitle());
                p.put("description", reminder.getDescription() != null ? reminder.getDescription() : "");
                p.put("priority", reminder.getPriority() != null ? reminder.getPriority().name() : "");

                if (Boolean.TRUE.equals(reminder.getSendEmail()) || Boolean.TRUE.equals(reminder.getSendSms())) {
                    notificationPort.notifyUser(reminder.getUser(), subject, "reminder-due", p, Set.of());
                }

                notificationPort.createInternalAlert(reminder.getUser(), subject,
                        "Your reminder \"" + reminder.getTitle() + "\" is due now."
                        + (reminder.getDescription() != null ? "\n\n" + reminder.getDescription() : "")
                        + "\n\nPriority: " + reminder.getPriority(),
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
                String subject = "Reminder: " + reminder.getTitle();
                String timeStr = reminder.getReminderTime() != null ? reminder.getReminderTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : "";
                Map<String, String> p = new HashMap<>();
                p.put("title", reminder.getTitle());
                p.put("description", reminder.getDescription() != null ? reminder.getDescription() : "");
                p.put("reminderTime", timeStr);
                p.put("reminderTimeSuffix", timeStr.isEmpty() ? "" : " at " + timeStr);
                p.put("priority", reminder.getPriority() != null ? reminder.getPriority().name() : "");

                if (Boolean.TRUE.equals(reminder.getSendEmail()) || Boolean.TRUE.equals(reminder.getSendSms())) {
                    notificationPort.notifyUser(reminder.getUser(), subject, "reminder-early", p, Set.of());
                }

                notificationPort.createInternalAlert(reminder.getUser(), subject,
                        "Reminder \"" + reminder.getTitle() + "\" is coming up in 5 minutes."
                        + (reminder.getDescription() != null ? "\n\n" + reminder.getDescription() : "")
                        + "\n\nTime: " + timeStr
                        + "\nPriority: " + reminder.getPriority(),
                        AlertType.REMINDER_DUE, reminder.getId(), "reminder");

                reminder.setEarlyAlertSent(true);
                reminderRepository.save(reminder);

                log.info("Early alert sent for reminder: {}", reminder.getId());
            } catch (Exception e) {
                log.error("Failed to send early alert for reminder {}: {}", reminder.getId(), e.getMessage());
            }
        }
    }

}
