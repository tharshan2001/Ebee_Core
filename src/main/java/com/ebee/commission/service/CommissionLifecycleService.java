package com.ebee.commission.service;

import com.ebee.agent.entity.Agent;
import com.ebee.alert.enums.AlertType;
import com.ebee.commission.dto.BatchPaymentResponse;
import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.notification.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
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

    @Transactional
    public void approveCommissionForOrder(UUID orderId) {
        commissionRepository.findCommissionByOrderId(orderId).ifPresent(commission -> {
            if (commission.getStatus() == CommissionStatus.CALCULATED) {
                approveCommission(commission.getId());
            }
        });
    }

    @Transactional
    public int processApprovedCommissions() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(10);
        List<Commission> commissions = commissionRepository.findApprovedCommissionsOlderThan(threshold);
        commissions.sort(Comparator.comparing(Commission::getApprovedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        int count = 0;
        for (Commission c : commissions) {
            try {
                c.setStatus(CommissionStatus.PROCESSING);
                commissionRepository.save(c);
                sendCommissionNotification(c, "Processing",
                        "Your commission of LKR %.2f for order %s has been automatically moved to processing after the 10-day holding period."
                                .formatted(c.getCommissionAmount(), c.getOrder().getOrderNumber()));
                count++;
            } catch (Exception e) {
                log.error("Failed to auto-process commission {} for order {}: {}",
                        c.getId(), c.getOrder().getOrderNumber(), e.getMessage());
            }
        }
        return count;
    }

    @Transactional
    public BatchPaymentResponse payBatch(List<UUID> commissionIds) {
        BigDecimal totalPaid = BigDecimal.ZERO;
        int count = 0;
        List<CommissionResponse> paidList = new ArrayList<>();

        for (UUID id : commissionIds) {
            Commission c = getCommissionEntity(id);
            if (c.getStatus() != CommissionStatus.PROCESSING) {
                throw new IllegalStateException(
                        "Commission " + id + " for order " + c.getOrder().getOrderNumber()
                        + " is not in PROCESSING status (current: " + c.getStatus() + ")");
            }
            c.setStatus(CommissionStatus.PAID);
            c.setPaidAt(LocalDateTime.now());

            Agent agent = c.getAgent();
            agent.setTotalCommission(agent.getTotalCommission() + c.getCommissionAmount().doubleValue());
            agent.setTotalSales(agent.getTotalSales() + c.getOrderAmount().doubleValue());

            commissionRepository.save(c);
            sendCommissionNotification(c, "Paid",
                    """
                            Your commission of LKR %.2f for order %s has been paid.
                            
                            Total earnings to date: LKR %.2f
                            """.formatted(
                            c.getCommissionAmount(), c.getOrder().getOrderNumber(),
                            agent.getTotalCommission()));

            totalPaid = totalPaid.add(c.getCommissionAmount());
            count++;
            paidList.add(mapper.toResponse(c));
        }

        BigDecimal remaining = commissionRepository.sumCommissionAmountByStatus(CommissionStatus.PROCESSING);

        return BatchPaymentResponse.builder()
                .totalPaid(totalPaid)
                .commissionsPaid(count)
                .remainingAvailable(remaining)
                .paidCommissions(paidList)
                .build();
    }

    private void sendCommissionNotification(Commission commission, String statusLabel, String message) {
        Agent agent = commission.getAgent();
        String subject = "Commission " + statusLabel + " – Order " + commission.getOrder().getOrderNumber();
        String fullMessage = "Dear %s,\n\n%s".formatted(agent.getUser().getFirstName(), message);
        notificationService.notifyAgent(agent, subject, fullMessage);

        AlertType alertType = switch (statusLabel) {
            case "Approved" -> AlertType.COMMISSION_APPROVED;
            case "Processing" -> AlertType.COMMISSION_PROCESSING;
            case "Paid" -> AlertType.COMMISSION_PAID;
            case "Rejected" -> AlertType.COMMISSION_REJECTED;
            case "Cancelled" -> AlertType.COMMISSION_CANCELLED;
            default -> null;
        };
        if (alertType != null) {
            notificationService.createInternalAlert(agent.getUser(), subject, fullMessage,
                    alertType, commission.getId(), "commission");
        }
    }

    private Commission getCommissionEntity(UUID id) {
        return commissionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Commission not found: " + id));
    }
}
