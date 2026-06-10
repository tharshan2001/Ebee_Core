package com.ebee.commission.service;

import com.ebee.alert.enums.AlertType;
import com.ebee.commission.entity.Commission;
import com.ebee.notification.NotificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommissionNotificationService {

    private final NotificationPort notificationPort;

    public void sendCommissionNotification(Commission commission, String statusLabel, String message) {
        var agent = commission.getAgent();
        String subject = "Commission " + statusLabel + " – Order " + commission.getOrder().getOrderNumber();
        String fullMessage = "Dear %s,\n\n%s".formatted(agent.getUser().getFirstName(), message);
        notificationPort.notifyAgent(agent, subject, fullMessage);

        AlertType alertType = switch (statusLabel) {
            case "Approved" -> AlertType.COMMISSION_APPROVED;
            case "Processing" -> AlertType.COMMISSION_PROCESSING;
            case "Paid" -> AlertType.COMMISSION_PAID;
            case "Rejected" -> AlertType.COMMISSION_REJECTED;
            case "Cancelled" -> AlertType.COMMISSION_CANCELLED;
            default -> null;
        };
        if (alertType != null) {
            notificationPort.createInternalAlert(agent.getUser(), subject, fullMessage,
                    alertType, commission.getId(), "commission");
        }
    }
}
