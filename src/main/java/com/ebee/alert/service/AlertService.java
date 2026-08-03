package com.ebee.alert.service;

import com.ebee.alert.entity.Alert;
import com.ebee.alert.repository.AlertRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    @Transactional(readOnly = true)
    public Page<Alert> getAlerts(UUID recipientId, Pageable pageable) {
        return alertRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID recipientId) {
        return alertRepository.countByRecipientIdAndIsReadFalse(recipientId);
    }

    @Transactional
    public Result<Void> markAsRead(UUID alertId, UUID recipientId) {
        return alertRepository.findById(alertId)
                .map(alert -> {
                    if (!alert.getRecipient().getId().equals(recipientId)) {
                        return Result.<Void>fail("Access denied");
                    }
                    alert.setIsRead(true);
                    alertRepository.save(alert);
                    return Result.<Void>ok(null);
                })
                .orElseGet(() -> Result.<Void>notFound("Alert", "id", alertId));
    }

    @Transactional
    public void markAllAsRead(UUID recipientId) {
        Page<Alert> unread = alertRepository.findByRecipientIdOrderByCreatedAtDesc(
                recipientId, PageRequest.of(0, Integer.MAX_VALUE));
        unread.getContent().stream()
                .filter(alert -> !Boolean.TRUE.equals(alert.getIsRead()))
                .forEach(alert -> {
                    alert.setIsRead(true);
                    alertRepository.save(alert);
                });
    }
}
