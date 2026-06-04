package com.ebee.reminder.dto;

import com.ebee.reminder.entity.Reminder;
import com.ebee.reminder.enums.ReminderPriority;
import com.ebee.reminder.enums.ReminderStatus;
import org.springframework.stereotype.Component;

@Component
public class ReminderMapper {

    public ReminderResponse toResponse(Reminder reminder) {
        ReminderResponse response = new ReminderResponse();
        response.setId(reminder.getId());
        response.setUserId(reminder.getUser().getId());
        response.setTitle(reminder.getTitle());
        response.setDescription(reminder.getDescription());
        response.setReminderDate(reminder.getReminderDate());
        response.setReminderTime(reminder.getReminderTime());
        response.setStatus(reminder.getStatus());
        response.setPriority(reminder.getPriority());
        response.setCategory(reminder.getCategory());
        response.setSendEmail(reminder.getSendEmail());
        response.setSendSms(reminder.getSendSms());
        response.setNotifiedAt(reminder.getNotifiedAt());
        response.setCompletedAt(reminder.getCompletedAt());
        response.setCreatedAt(reminder.getCreatedAt());
        response.setUpdatedAt(reminder.getUpdatedAt());
        return response;
    }

    public Reminder toEntity(ReminderRequest request) {
        Reminder reminder = new Reminder();
        reminder.setTitle(request.getTitle());
        reminder.setDescription(request.getDescription());
        reminder.setReminderDate(request.getReminderDate());
        reminder.setReminderTime(request.getReminderTime());
        reminder.setPriority(request.getPriority() != null ? request.getPriority() : ReminderPriority.MEDIUM);
        reminder.setCategory(request.getCategory());
        reminder.setSendEmail(request.getSendEmail() != null ? request.getSendEmail() : false);
        reminder.setSendSms(request.getSendSms() != null ? request.getSendSms() : false);
        return reminder;
    }

    public void updateEntity(Reminder reminder, ReminderRequest request) {
        reminder.setTitle(request.getTitle());
        reminder.setDescription(request.getDescription());
        reminder.setReminderDate(request.getReminderDate());
        reminder.setReminderTime(request.getReminderTime());
        if (request.getPriority() != null) reminder.setPriority(request.getPriority());
        reminder.setCategory(request.getCategory());
        if (request.getSendEmail() != null) reminder.setSendEmail(request.getSendEmail());
        if (request.getSendSms() != null) reminder.setSendSms(request.getSendSms());
    }
}
