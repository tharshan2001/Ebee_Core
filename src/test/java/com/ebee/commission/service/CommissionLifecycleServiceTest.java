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
                .status(CommissionStatus.CALCULATED)
                .commissionAmount(new BigDecimal("500.00"))
                .build();
    }

    @Test
    void approveCommission_transitionsToApproved() {
        when(commissionRepository.findById(commissionId)).thenReturn(Optional.of(commission));
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
        when(commissionRepository.findById(commissionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commissionLifecycleService.approveCommission(commissionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Commission not found");
    }

    @Test
    void markAsProcessing_transitionsToProcessing() {
        commission.setStatus(CommissionStatus.APPROVED);
        when(commissionRepository.findById(commissionId)).thenReturn(Optional.of(commission));
        when(commissionRepository.save(any())).thenReturn(commission);
        when(mapper.toResponse(commission)).thenReturn(new CommissionResponse());

        commissionLifecycleService.markAsProcessing(commissionId);

        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.PROCESSING);
    }

    @Test
    void rejectCommission_transitionsToRejected() {
        commission.setStatus(CommissionStatus.APPROVED);
        when(commissionRepository.findById(commissionId)).thenReturn(Optional.of(commission));
        when(commissionRepository.save(any())).thenReturn(commission);
        when(mapper.toResponse(commission)).thenReturn(new CommissionResponse());

        commissionLifecycleService.rejectCommission(commissionId, "Invalid documentation");

        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.REJECTED);
        assertThat(commission.getErrorMessage()).isEqualTo("Invalid documentation");
    }
}
