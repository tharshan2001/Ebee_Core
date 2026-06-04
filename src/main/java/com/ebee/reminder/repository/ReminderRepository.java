package com.ebee.reminder.repository;

import com.ebee.reminder.entity.Reminder;
import com.ebee.reminder.enums.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    List<Reminder> findByUserIdAndReminderDateBetweenOrderByReminderTimeAsc(
            UUID userId, LocalDate start, LocalDate end);

    List<Reminder> findByUserIdAndStatusOrderByReminderDateDesc(UUID userId, ReminderStatus status);

    @Query("SELECT r FROM Reminder r WHERE r.status = 'PENDING' AND r.reminderDate = :date "
            + "AND (r.reminderTime IS NULL OR r.reminderTime <= :now) "
            + "AND r.alertSent = false AND (r.sendEmail = true OR r.sendSms = true)")
    List<Reminder> findPendingRemindersForAlert(
            @Param("date") LocalDate date, @Param("now") LocalTime now);

    @Query("SELECT r FROM Reminder r WHERE r.status = 'PENDING' AND r.reminderDate = :date "
            + "AND r.reminderTime IS NOT NULL "
            + "AND r.reminderTime > :earlyBound AND r.reminderTime <= :lateBound "
            + "AND r.earlyAlertSent = false AND (r.sendEmail = true OR r.sendSms = true)")
    List<Reminder> findPendingRemindersForEarlyAlert(
            @Param("date") LocalDate date,
            @Param("earlyBound") LocalTime earlyBound,
            @Param("lateBound") LocalTime lateBound);
}
