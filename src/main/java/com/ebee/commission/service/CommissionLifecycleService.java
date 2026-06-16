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
import com.ebee.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        commission.setStatus(CommissionStatus.REJECTED);
        commission.setErrorMessage(reason);
        commission = commissionRepository.save(commission);
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
                Agent agent = commission.getAgent();
                agent.setTotalCommission(agent.getTotalCommission() - commission.getCommissionAmount().doubleValue());
                agent.setTotalSales(agent.getTotalSales() - commission.getOrderAmount().doubleValue());
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

    private Commission getCommissionEntity(UUID id) {
        return commissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commission not found: " + id));
    }
}
