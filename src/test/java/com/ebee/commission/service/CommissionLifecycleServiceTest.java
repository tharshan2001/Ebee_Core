package com.ebee.commission.service;

import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.commission.dto.CommissionResponse;
import com.ebee.commission.entity.Commission;
import com.ebee.commission.enums.CommissionStatus;
import com.ebee.commission.mapper.CommissionMapper;
import com.ebee.commission.repository.CommissionRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommissionLifecycleServiceTest {

    @Mock private CommissionRepository commissionRepository;
    @Mock private CommissionMapper mapper;
    @Mock private CommissionNotificationService commissionNotificationService;
    @Mock private CommissionPaymentService commissionPaymentService;
    @Mock private AuditLogService auditLogService;
    @Mock private com.ebee.accounting.service.LedgerService ledgerService;
    @Mock private com.ebee.common.security.SecurityService securityService;
    @Mock private com.ebee.agent.repository.AgentRepository agentRepository;

    @InjectMocks private CommissionLifecycleService commissionLifecycleService;

    private Commission commission;
    private UUID commissionId;

    @BeforeEach
    void setUp() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("COL-A-20250601-001")
                .status(OrderStatus.PENDING)
                .build();

        commissionId = UUID.randomUUID();
        commission = Commission.builder()
                .id(commissionId)
                .order(order)
                .agent(com.ebee.agent.entity.Agent.builder().id(UUID.randomUUID()).build())
                .status(CommissionStatus.CALCULATED)
                .commissionAmount(new BigDecimal("500.00"))
                .build();
    }

    @Test
    void approveCommission_transitionsToApproved() {
        when(commissionRepository.findByIdWithDetails(commissionId)).thenReturn(Optional.of(commission));
        when(commissionRepository.save(any())).thenReturn(commission);
        when(mapper.toResponse(commission)).thenReturn(new CommissionResponse());

        commissionLifecycleService.approveCommission(commissionId);

        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.APPROVED);
        assertThat(commission.getApprovedAt()).isNotNull();
        verify(auditLogService).record(eq(AuditAction.COMMISSION_APPROVE), eq("Commission"), eq(commissionId), anyString());
        verify(commissionNotificationService).sendCommissionNotification(eq(commission), eq("Approved"), anyString());
    }

    @Test
    void approveCommission_notFound_throws() {
        when(commissionRepository.findByIdWithDetails(commissionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commissionLifecycleService.approveCommission(commissionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Commission not found");
    }

    @Test
    void markAsProcessing_transitionsToProcessing() {
        commission.setStatus(CommissionStatus.APPROVED);
        commission.setApprovedAt(LocalDateTime.now().minusDays(11));
        when(commissionRepository.findByIdWithDetails(commissionId)).thenReturn(Optional.of(commission));
        when(commissionRepository.save(any())).thenReturn(commission);
        when(mapper.toResponse(commission)).thenReturn(new CommissionResponse());

        commissionLifecycleService.markAsProcessing(commissionId);

        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.PROCESSING);
    }

    @Test
    void rejectCommission_transitionsToRejected() {
        commission.setStatus(CommissionStatus.APPROVED);
        when(commissionRepository.findByIdWithDetails(commissionId)).thenReturn(Optional.of(commission));
        when(commissionRepository.save(any())).thenReturn(commission);
        when(mapper.toResponse(commission)).thenReturn(new CommissionResponse());

        commissionLifecycleService.rejectCommission(commissionId, "Invalid documentation");

        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.REJECTED);
        assertThat(commission.getErrorMessage()).isEqualTo("Invalid documentation");
        // The calculation credit is reversed so the agent ledger no longer shows it as owed.
        verify(ledgerService).recordAgentPayout(eq(commission.getAgent().getId()), isNull(),
                argThat(a -> a.compareTo(new BigDecimal("500.00")) == 0), contains("rejected"), any());
    }

    @Test
    void approveCommission_whenNotCalculated_throws() {
        commission.setStatus(CommissionStatus.APPROVED);
        when(commissionRepository.findByIdWithDetails(commissionId)).thenReturn(Optional.of(commission));

        assertThatThrownBy(() -> commissionLifecycleService.approveCommission(commissionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be CALCULATED");
    }

    @Test
    void markAsProcessing_whenNotApproved_throws() {
        when(commissionRepository.findByIdWithDetails(commissionId)).thenReturn(Optional.of(commission));

        assertThatThrownBy(() -> commissionLifecycleService.markAsProcessing(commissionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be APPROVED");
    }

    @Test
    void markAsProcessing_whenWithinHoldingPeriod_throws() {
        commission.setStatus(CommissionStatus.APPROVED);
        commission.setApprovedAt(LocalDateTime.now().minusDays(5));
        when(commissionRepository.findByIdWithDetails(commissionId)).thenReturn(Optional.of(commission));

        assertThatThrownBy(() -> commissionLifecycleService.markAsProcessing(commissionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("holding period");
    }

    @Test
    void rejectCommission_whenTerminalStatus_throws() {
        commission.setStatus(CommissionStatus.PAID);
        when(commissionRepository.findByIdWithDetails(commissionId)).thenReturn(Optional.of(commission));

        assertThatThrownBy(() -> commissionLifecycleService.rejectCommission(commissionId, "reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot reject");
    }

    @Test
    void cancelCommissionForOrder_whenAlreadyCancelled_doesNothing() {
        commission.setStatus(CommissionStatus.CANCELLED);
        when(commissionRepository.findCommissionByOrderId(commission.getOrder().getId()))
                .thenReturn(Optional.of(commission));

        commissionLifecycleService.cancelCommissionForOrder(commission.getOrder().getId());

        verify(commissionNotificationService, never()).sendCommissionNotification(any(), any(), any());
        verify(auditLogService, never()).record(any(), anyString(), any(), anyString());
    }

    @Test
    void cancelCommissionForOrder_whenAlreadyRejected_doesNothing() {
        commission.setStatus(CommissionStatus.REJECTED);
        when(commissionRepository.findCommissionByOrderId(commission.getOrder().getId()))
                .thenReturn(Optional.of(commission));

        commissionLifecycleService.cancelCommissionForOrder(commission.getOrder().getId());

        verify(commissionNotificationService, never()).sendCommissionNotification(any(), any(), any());
        verify(auditLogService, never()).record(any(), anyString(), any(), anyString());
    }
}
