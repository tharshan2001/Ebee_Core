package com.ebee.commission.service;

import com.ebee.agent.entity.Agent;
import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.commission.dto.BatchPaymentResponse;
import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionRepository;
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
public class CommissionPaymentService {

    private final CommissionRepository commissionRepository;
    private final CommissionMapper mapper;
    private final CommissionNotificationService commissionNotificationService;
    private final AuditLogService auditLogService;

    @Transactional
    public CommissionResponse markAsPaid(UUID commissionId) {
        Commission commission = getCommissionEntity(commissionId);
        commission.setStatus(CommissionStatus.PAID);
        commission.setPaidAt(LocalDateTime.now());
        commission = commissionRepository.save(commission);

        auditLogService.record(AuditAction.COMMISSION_PAY, "Commission", commissionId,
                "Commission LKR " + commission.getCommissionAmount() + " for order "
                + commission.getOrder().getOrderNumber() + " paid");

        Agent agent = commission.getAgent();
        agent.setTotalCommission(agent.getTotalCommission() + commission.getCommissionAmount().doubleValue());
        agent.setTotalSales(agent.getTotalSales() + commission.getOrderAmount().doubleValue());

        commissionNotificationService.sendCommissionNotification(commission, "Paid",
                """
                        Your commission of LKR %.2f for order %s has been paid.
                        
                        Total earnings to date: LKR %.2f
                        """.formatted(
                        commission.getCommissionAmount(), commission.getOrder().getOrderNumber(),
                        agent.getTotalCommission()));

        return mapper.toResponse(commission);
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
            auditLogService.record(AuditAction.COMMISSION_PAY, "Commission", c.getId(),
                    "Commission LKR " + c.getCommissionAmount() + " for order "
                    + c.getOrder().getOrderNumber() + " paid via batch payment");
            commissionNotificationService.sendCommissionNotification(c, "Paid",
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

    private Commission getCommissionEntity(UUID id) {
        return commissionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Commission not found: " + id));
    }
}
