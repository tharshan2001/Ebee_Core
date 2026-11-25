package com.ebee.commission.service;

import com.ebee.accounting.service.LedgerService;
import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.security.SecurityService;
import com.ebee.commission.dto.BatchPaymentResponse;
import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ebee.order.entity.Order;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionLifecycleService {

    private final CommissionRepository commissionRepository;
    private final CommissionMapper mapper;
    private final CommissionNotificationService commissionNotificationService;
    private final CommissionPaymentService commissionPaymentService;
    private final AuditLogService auditLogService;
    private final LedgerService ledgerService;
    private final SecurityService securityService;
    private final AgentRepository agentRepository;

    @Transactional
    public CommissionResponse approveCommission(UUID commissionId) {
        Commission commission = getCommissionEntity(commissionId);
        if (commission.getStatus() != CommissionStatus.CALCULATED) {
            throw new IllegalStateException(
                    "Commission " + commissionId + " must be CALCULATED to approve (current: "
                    + commission.getStatus() + ")");
        }
        commission.setStatus(CommissionStatus.APPROVED);
        commission.setApprovedAt(LocalDateTime.now());
        commission = commissionRepository.save(commission);
        auditLogService.record(AuditAction.COMMISSION_APPROVE, "Commission", commissionId,
                "Commission LKR " + commission.getCommissionAmount() + " for order "
                + commission.getOrder().getOrderNumber() + " approved");
        commissionNotificationService.sendCommissionNotification(commission, "Approved",
                "It has been approved and is pending processing.");
        return mapper.toResponse(commission);
    }

    @Transactional
    public CommissionResponse markAsProcessing(UUID commissionId) {
        Commission commission = getCommissionEntity(commissionId);
        if (commission.getStatus() != CommissionStatus.APPROVED) {
            throw new IllegalStateException(
                    "Commission " + commissionId + " must be APPROVED to process (current: "
                    + commission.getStatus() + ")");
        }
        if (commission.getApprovedAt() == null
                || commission.getApprovedAt().plusDays(10).isAfter(LocalDateTime.now())) {
            LocalDateTime releaseDate = commission.getApprovedAt() != null
                    ? commission.getApprovedAt().plusDays(10) : null;
            throw new IllegalStateException(
                    "Commission " + commissionId + " is within the 10-day holding period. "
                    + "It can be processed after " + releaseDate + ".");
        }
        commission.setStatus(CommissionStatus.PROCESSING);
        commission = commissionRepository.save(commission);
        auditLogService.record(AuditAction.COMMISSION_PROCESS, "Commission", commissionId,
                "Commission LKR " + commission.getCommissionAmount() + " for order "
                + commission.getOrder().getOrderNumber() + " moved to processing");
        commissionNotificationService.sendCommissionNotification(commission, "Processing",
                "It is now being processed for payment.");
        return mapper.toResponse(commission);
    }

    @Transactional
    public CommissionResponse markAsPaid(UUID commissionId) {
        return commissionPaymentService.markAsPaid(commissionId);
    }

    @Transactional
    public CommissionResponse rejectCommission(UUID commissionId, String reason) {
        Commission commission = getCommissionEntity(commissionId);
        if (commission.getStatus() == CommissionStatus.PAID
                || commission.getStatus() == CommissionStatus.PROCESSING
                || commission.getStatus() == CommissionStatus.REJECTED
                || commission.getStatus() == CommissionStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot reject commission " + commissionId + " in status "
                    + commission.getStatus());
        }
        boolean creditWasPosted = commission.getStatus() == CommissionStatus.CALCULATED
                || commission.getStatus() == CommissionStatus.APPROVED;
        commission.setStatus(CommissionStatus.REJECTED);
        commission.setErrorMessage(reason);
        commission = commissionRepository.save(commission);
        if (creditWasPosted) {
            // The CREDIT posted at calculation must be reversed, or the agent ledger keeps showing it as owed.
            ledgerService.recordAgentPayout(commission.getAgent().getId(), null, commission.getCommissionAmount(),
                    "Commission rejected for Order " + commission.getOrder().getOrderNumber() + " (reversal)",
                    securityService.getCurrentUserId());
        }
        auditLogService.record(AuditAction.COMMISSION_REJECT, "Commission", commissionId,
                "Commission LKR " + commission.getCommissionAmount() + " for order "
                + commission.getOrder().getOrderNumber() + " rejected: " + reason);
        commissionNotificationService.sendCommissionNotification(commission, "Rejected",
                "Reason: " + reason + ". Please contact support for more information.");
        return mapper.toResponse(commission);
    }

    @Transactional
    public void cancelCommissionForOrder(UUID orderId) {
        commissionRepository.findCommissionByOrderId(orderId).ifPresent(commission -> {

            if (commission.getStatus() == CommissionStatus.CANCELLED
                    || commission.getStatus() == CommissionStatus.REJECTED) {
                log.warn("Commission for order {} is already {}, skipping cancel",
                        orderId, commission.getStatus());
                return;
            }

            if (commission.getStatus() == CommissionStatus.PAID) {
                // Already paid out: the agent owes back whatever they haven't already returned (D2).
                Agent agent = commission.getAgent();
                agent.setTotalSales(agent.getTotalSales().subtract(commission.getOrderAmount()));
                clawBack(commission, commission.getEarnedAmount(),
                        "order " + commission.getOrder().getOrderNumber() + " cancelled/returned");
            } else {
                // Commission was CALCULATED/APPROVED/PROCESSING — reverse the CREDIT with a DEBIT
                ledgerService.recordAgentPayout(
                        commission.getAgent().getId(),
                        null,
                        commission.getCommissionAmount(),
                        "Commission cancelled for Order " + commission.getOrder().getOrderNumber() + " (reversal)",
                        securityService.getCurrentUserId()
                );
            }

            commission.setStatus(CommissionStatus.CANCELLED);

            auditLogService.record(AuditAction.COMMISSION_CANCEL, "Commission", commission.getId(),
                    "Commission LKR " + commission.getCommissionAmount() + " for order "
                    + commission.getOrder().getOrderNumber() + " cancelled");

            commissionNotificationService.sendCommissionNotification(commission, "Cancelled",
                    "It has been cancelled as the order was cancelled.");
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
        return commissionPaymentService.processApprovedCommissions();
    }

    @Transactional
    public BatchPaymentResponse payBatch(List<UUID> commissionIds) {
        return commissionPaymentService.payBatch(commissionIds);
    }

    /**
     * Re-base the order's commission on its current total after a partial return (D1).
     * A fully returned order cancels the commission instead. Already-paid commission that is
     * now too high is clawed back (D2).
     */
    @Transactional
    public void adjustCommissionForOrder(Order order) {
        boolean fullyReturned = order.getItems().stream().allMatch(i -> i.isFullyReturned());
        if (fullyReturned || order.getTotalAmount().signum() == 0) {
            cancelCommissionForOrder(order.getId());
            return;
        }
        commissionRepository.findCommissionByOrderId(order.getId()).ifPresent(commission -> {
            if (commission.getStatus() == CommissionStatus.CANCELLED
                    || commission.getStatus() == CommissionStatus.REJECTED) {
                return;
            }
            BigDecimal newAmount = order.getTotalAmount()
                    .multiply(commission.getCommissionRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            Agent agent = commission.getAgent();
            BigDecimal salesDrop = commission.getOrderAmount().subtract(order.getTotalAmount());
            String label = "order " + order.getOrderNumber() + " partly returned";

            if (commission.getStatus() == CommissionStatus.PAID) {
                BigDecimal excess = commission.getEarnedAmount().subtract(newAmount);
                if (excess.signum() > 0) {
                    clawBack(commission, excess, label);
                }
                agent.setTotalSales(agent.getTotalSales().subtract(salesDrop));
            } else {
                BigDecimal delta = commission.getCommissionAmount().subtract(newAmount);
                UUID actor = securityService.getCurrentUserId();
                if (delta.signum() > 0) {
                    ledgerService.recordAgentPayout(agent.getId(), null, delta,
                            "Commission reduced: " + label + " (reversal)", actor);
                } else if (delta.signum() < 0) {
                    ledgerService.recordAgentCommission(agent.getId(), commission.getId(), delta.negate(),
                            "Commission increased: " + label, actor);
                }
                commission.setCommissionAmount(newAmount);
            }
            commission.setOrderAmount(order.getTotalAmount());
            commissionRepository.save(commission);
            auditLogService.record(AuditAction.COMMISSION_CANCEL, "Commission", commission.getId(),
                    "Commission for " + label + ": now LKR " + newAmount + " on order total " + order.getTotalAmount());
        });
    }

    /**
     * The agent owes {@code amount} back from an already-paid commission: debit the agent ledger
     * (balance goes negative = agent owes) and add it to the agent's clawback balance, which is
     * recovered from their next commission payments.
     */
    private void clawBack(Commission commission, BigDecimal amount, String reason) {
        if (amount.signum() <= 0) {
            return;
        }
        Agent agent = commission.getAgent();
        commission.setClawbackAmount(commission.getClawbackAmount().add(amount));
        agent.setClawbackBalance(agent.getClawbackBalance().add(amount));
        agent.setTotalCommission(agent.getTotalCommission().subtract(amount));
        ledgerService.recordAgentPayout(agent.getId(), null, amount,
                "Commission clawback: " + reason, securityService.getCurrentUserId());
        commissionRepository.save(commission);
        agentRepository.save(agent);
        auditLogService.record(AuditAction.COMMISSION_CANCEL, "Commission", commission.getId(),
                "Clawback LKR " + amount + " (" + reason + "); agent now owes LKR " + agent.getClawbackBalance());
    }

    public void syncAgentTotalCommission(UUID agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found: " + agentId));
        BigDecimal totalPaid = commissionRepository.sumCommissionAmountByAgentIdAndStatus(agentId, CommissionStatus.PAID);
        BigDecimal totalSales = commissionRepository.sumSalesByAgentId(agentId);
        agent.setTotalCommission(totalPaid != null ? totalPaid : BigDecimal.ZERO);
        agent.setTotalSales(totalSales != null ? totalSales : BigDecimal.ZERO);
        agentRepository.save(agent);
    }

    private Commission getCommissionEntity(UUID id) {
        return commissionRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commission not found: " + id));
    }
}
