package com.ebee.commission.service;

import com.ebee.accounting.service.LedgerService;
import com.ebee.agent.entity.Agent;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionPaymentService {

    private final CommissionRepository commissionRepository;
    private final CommissionMapper mapper;
    private final CommissionNotificationService commissionNotificationService;
    private final AuditLogService auditLogService;
    private final LedgerService ledgerService;
    private final SecurityService securityService;

    @Transactional
    public CommissionResponse markAsPaid(UUID commissionId) {
        Commission commission = getCommissionEntity(commissionId);

        if (commission.getStatus() == CommissionStatus.PAID) {
            throw new IllegalStateException("Commission " + commissionId + " is already paid");
        }
        if (commission.getStatus() != CommissionStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Commission " + commissionId + " must be in PROCESSING status to mark as paid (current: "
                    + commission.getStatus() + ")");
        }

        commission.setStatus(CommissionStatus.PAID);
        commission.setPaidAt(LocalDateTime.now());
        commission = commissionRepository.save(commission);

        auditLogService.record(AuditAction.COMMISSION_PAY, "Commission", commissionId,
                "Commission LKR " + commission.getCommissionAmount() + " for order "
                + commission.getOrder().getOrderNumber() + " paid");

        Agent agent = commission.getAgent();
        agent.setTotalCommission(agent.getTotalCommission().add(commission.getCommissionAmount()));
        agent.setTotalSales(agent.getTotalSales().add(commission.getOrderAmount()));
        settlePayout(commission, "Commission paid for Order " + commission.getOrder().getOrderNumber());
        commissionRepository.save(commission);

        commissionNotificationService.sendCommissionNotification(commission, "Paid",
                "It has been paid. Total earnings to date: LKR "
                + agent.getTotalCommission().setScale(2, RoundingMode.HALF_UP).toPlainString() + ".");

        return mapper.toResponse(commission);
    }

    /**
     * Pay a commission net of any clawback the agent still owes (D2). Only the cash actually paid
     * is debited to the agent ledger — the clawback was already debited when it arose.
     */
    private void settlePayout(Commission commission, String description) {
        Agent agent = commission.getAgent();
        BigDecimal recovered = commission.getCommissionAmount().min(agent.getClawbackBalance());
        BigDecimal cash = commission.getCommissionAmount().subtract(recovered);
        agent.setClawbackBalance(agent.getClawbackBalance().subtract(recovered));
        commission.setPaidAmount(cash);
        if (cash.signum() > 0) {
            ledgerService.recordAgentPayout(agent.getId(), null, cash, description, securityService.getCurrentUserId());
        }
        if (recovered.signum() > 0) {
            auditLogService.record(AuditAction.COMMISSION_PAY, "Commission", commission.getId(),
                    "Recovered LKR " + recovered + " of earlier clawbacks; paid LKR " + cash
                    + "; agent still owes LKR " + agent.getClawbackBalance());
        }
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
                commissionNotificationService.sendCommissionNotification(c, "Processing",
                        "It has been automatically moved to processing after the 10-day holding period.");
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
            agent.setTotalCommission(agent.getTotalCommission() != null ? agent.getTotalCommission().add(c.getCommissionAmount()) : c.getCommissionAmount());
            agent.setTotalSales(agent.getTotalSales() != null ? agent.getTotalSales().add(c.getOrderAmount()) : c.getOrderAmount());

            settlePayout(c, "Commission paid (batch) for Order " + c.getOrder().getOrderNumber());

            commissionRepository.save(c);
            auditLogService.record(AuditAction.COMMISSION_PAY, "Commission", c.getId(),
                    "Commission LKR " + c.getCommissionAmount() + " for order "
                    + c.getOrder().getOrderNumber() + " paid via batch payment");
            commissionNotificationService.sendCommissionNotification(c, "Paid",
                    "It has been paid. Total earnings to date: LKR "
                    + agent.getTotalCommission().setScale(2, RoundingMode.HALF_UP).toPlainString() + ".");

            totalPaid = totalPaid.add(c.getNetPaidAmount());
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

    private Commission getCommissionEntity(UUID id) {
        return commissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commission not found: " + id));
    }
}
