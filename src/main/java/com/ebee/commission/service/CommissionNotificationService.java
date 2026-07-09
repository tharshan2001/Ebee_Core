package com.ebee.commission.service;

import com.ebee.alert.enums.AlertType;
import com.ebee.commission.entity.Commission;
import com.ebee.common.util.NumberUtils;
import com.ebee.notification.NotificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommissionNotificationService {

    private final NotificationPort notificationPort;

    private static final java.util.Set<String> MAJOR_STATUSES = java.util.Set.of("Paid", "Cancelled");

    public void sendCommissionNotification(Commission commission, String statusLabel, String statusMessage) {
        var agent = commission.getAgent();
        String subject = "Commission " + statusLabel + " – Order " + commission.getOrder().getOrderNumber();

        if (MAJOR_STATUSES.contains(statusLabel)) {
            Map<String, String> p = new HashMap<>();
            p.put("agentName", agent.getUser().getFirstName());
            p.put("orderNumber", commission.getOrder().getOrderNumber());
            p.put("statusLabel", statusLabel);
            p.put("commissionAmount", NumberUtils.formatAmount(commission.getCommissionAmount()));
            p.put("statusMessage", statusMessage);

            notificationPort.notifyAgent(agent, subject, "commission-status-changed", p, Set.of());
        }

        AlertType alertType = switch (statusLabel) {
            case "Approved" -> AlertType.COMMISSION_APPROVED;
            case "Processing" -> AlertType.COMMISSION_PROCESSING;
            case "Paid" -> AlertType.COMMISSION_PAID;
            case "Rejected" -> AlertType.COMMISSION_REJECTED;
            case "Cancelled" -> AlertType.COMMISSION_CANCELLED;
            default -> null;
        };
        if (alertType != null) {
            String alertMsg = "Commission " + statusLabel + " for order "
                    + commission.getOrder().getOrderNumber() + ". " + statusMessage;
            notificationPort.createInternalAlert(agent.getUser(), subject, alertMsg,
                    alertType, commission.getId(), "commission");
        }
    }
}
