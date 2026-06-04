package com.ebee.reminder.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.reminder.dto.ReminderMapper;
import com.ebee.reminder.dto.ReminderRequest;
import com.ebee.reminder.dto.ReminderResponse;
import com.ebee.reminder.entity.Reminder;
import com.ebee.reminder.enums.ReminderStatus;
import com.ebee.reminder.repository.ReminderRepository;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final ReminderMapper reminderMapper;
    private final UserRepository userRepository;

    public List<ReminderResponse> getRemindersByDateRange(UUID userId, LocalDate start, LocalDate end) {
        return reminderRepository.findByUserIdAndReminderDateBetweenOrderByReminderTimeAsc(userId, start, end)
                .stream()
                .map(reminderMapper::toResponse)
                .toList();
    }

    public List<ReminderResponse> getTodayReminders(UUID userId) {
        LocalDate today = LocalDate.now();
        return reminderRepository.findByUserIdAndReminderDateBetweenOrderByReminderTimeAsc(userId, today, today)
                .stream()
                .map(reminderMapper::toResponse)
                .toList();
    }

    public ReminderResponse getReminderById(UUID id) {
        Reminder reminder = reminderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder", "id", id));
        return reminderMapper.toResponse(reminder);
    }

    @Transactional
    public ReminderResponse createReminder(UUID userId, ReminderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Reminder reminder = reminderMapper.toEntity(request);
        reminder.setUser(user);

        return reminderMapper.toResponse(reminderRepository.save(reminder));
    }

    @Transactional
    public ReminderResponse updateReminder(UUID id, ReminderRequest request) {
        Reminder reminder = reminderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder", "id", id));

        reminderMapper.updateEntity(reminder, request);
        return reminderMapper.toResponse(reminderRepository.save(reminder));
    }

    @Transactional
    public void deleteReminder(UUID id) {
        if (!reminderRepository.existsById(id)) {
            throw new ResourceNotFoundException("Reminder", "id", id);
        }
        reminderRepository.deleteById(id);
    }

    @Transactional
    public ReminderResponse completeReminder(UUID id) {
        Reminder reminder = reminderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder", "id", id));

        reminder.setStatus(ReminderStatus.COMPLETED);
        reminder.setCompletedAt(LocalDateTime.now());
        return reminderMapper.toResponse(reminderRepository.save(reminder));
    }
}
