package com.ebee.commission.service;

import com.ebee.agent.entity.Agent;
import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.notification.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommissionLifecycleService {

    private final CommissionRepository commissionRepository;
    private final CommissionMapper mapper;
    private final NotificationService notificationService;

    @Transactional
    public CommissionResponse approveCommission(UUID commissionId) {
        Commission commission = getCommissionEntity(commissionId);
        commission.setStatus(CommissionStatus.APPROVED);
        commission.setApprovedAt(LocalDateTime.now());
        commission = commissionRepository.save(commission);
        sendCommissionNotification(commission, "Approved",
                "Your commission of LKR %.2f for order %s has been approved and is pending processing.".formatted(
                        commission.getCommissionAmount(), commission.getOrder().getOrderNumber()));
        return mapper.toResponse(commission);
    }

    @Transactional
    public CommissionResponse markAsProcessing(UUID commissionId) {
        Commission commission = getCommissionEntity(commissionId);
        commission.setStatus(CommissionStatus.PROCESSING);
        commission = commissionRepository.save(commission);
        sendCommissionNotification(commission, "Processing",
                "Your commission of LKR %.2f for order %s is now being processed for payment.".formatted(
                        commission.getCommissionAmount(), commission.getOrder().getOrderNumber()));
        return mapper.toResponse(commission);
    }

    @Transactional
    public CommissionResponse markAsPaid(UUID commissionId) {
        Commission commission = getCommissionEntity(commissionId);
        commission.setStatus(CommissionStatus.PAID);
        commission.setPaidAt(LocalDateTime.now());
        commission = commissionRepository.save(commission);

        Agent agent = commission.getAgent();
        agent.setTotalCommission(agent.getTotalCommission() + commission.getCommissionAmount().doubleValue());
        agent.setTotalSales(agent.getTotalSales() + commission.getOrderAmount().doubleValue());

        sendCommissionNotification(commission, "Paid",
                """
                        Your commission of LKR %.2f for order %s has been paid.
                        
                        Total earnings to date: LKR %.2f
                        """.formatted(
                        commission.getCommissionAmount(), commission.getOrder().getOrderNumber(),
                        agent.getTotalCommission()));

        return mapper.toResponse(commission);
    }

    @Transactional
    public CommissionResponse rejectCommission(UUID commissionId, String reason) {
        Commission commission = getCommissionEntity(commissionId);
        commission.setStatus(CommissionStatus.REJECTED);
        commission.setErrorMessage(reason);
        commission = commissionRepository.save(commission);
        sendCommissionNotification(commission, "Rejected",
                "Your commission for order %s has been rejected.\n\nReason: %s\n\nPlease contact support for more information."
                        .formatted(commission.getOrder().getOrderNumber(), reason));
        return mapper.toResponse(commission);
    }

    @Transactional
    public void cancelCommissionForOrder(UUID orderId) {
        commissionRepository.findCommissionByOrderId(orderId).ifPresent(commission -> {

            if (commission.getStatus() == CommissionStatus.PAID) {
                Agent agent = commission.getAgent();
                agent.setTotalCommission(agent.getTotalCommission() - commission.getCommissionAmount().doubleValue());
                agent.setTotalSales(agent.getTotalSales() - commission.getOrderAmount().doubleValue());
            }

            commission.setStatus(CommissionStatus.CANCELLED);

            sendCommissionNotification(commission, "Cancelled",
                    "Your commission for order %s has been cancelled as the order was cancelled.".formatted(
                            commission.getOrder().getOrderNumber()));
        });
    }

    private void sendCommissionNotification(Commission commission, String statusLabel, String message) {
        Agent agent = commission.getAgent();
        String subject = "Commission " + statusLabel + " – Order " + commission.getOrder().getOrderNumber();
        String fullMessage = "Dear %s,\n\n%s".formatted(agent.getUser().getFirstName(), message);
        notificationService.notifyAgent(agent, subject, fullMessage);
    }

    private Commission getCommissionEntity(UUID id) {
        return commissionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Commission not found: " + id));
    }
}
