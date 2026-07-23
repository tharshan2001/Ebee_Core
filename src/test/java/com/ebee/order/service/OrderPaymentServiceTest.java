package com.ebee.order.service;

import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.customer.entity.Customer;
import com.ebee.order.dto.RecordPaymentRequest;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.PaymentMethod;
import com.ebee.order.enums.PaymentStatus;
import com.ebee.order.mapper.OrderMapper;
import com.ebee.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
class OrderPaymentServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private OrderPaymentService orderPaymentService;

    @Captor private ArgumentCaptor<Order> orderCaptor;

    private Customer customer;

    private Order unpaidOrder(BigDecimal totalAmount, PaymentMethod paymentMethod) {
        customer = Customer.builder()
                .id(UUID.randomUUID())
                .outstandingBalance(BigDecimal.ZERO)
                .creditLimit(BigDecimal.ZERO)
                .customerCode("CU-123456")
                .build();

        return Order.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .totalAmount(totalAmount)
                .paymentMethod(paymentMethod)
                .paymentStatus(PaymentStatus.UNPAID)
                .paidAmount(BigDecimal.ZERO)
                .outstandingBalance(totalAmount)
                .build();
    }

    @Test
    void recordPayment_onUnpaidOrder_marksAsPaid() {
        Order order = unpaidOrder(new BigDecimal("2000.00"), PaymentMethod.COD);

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());
        when(orderRepository.sumOutstandingByCustomerId(customer.getId())).thenReturn(BigDecimal.ZERO);

        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("2000.00"), PaymentMethod.CASH);
        orderPaymentService.recordPayment(order.getId(), request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(saved.getPaidAt()).isNotNull();

        verify(auditLogService).record(eq(AuditAction.UPDATE), eq("Order"), eq(order.getId()), anyString());
    }

    @Test
    void recordPayment_partialPaymentOnUnpaidOrder_setsPartiallyPaid() {
        Order order = unpaidOrder(new BigDecimal("2000.00"), PaymentMethod.COD);

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());
        when(orderRepository.sumOutstandingByCustomerId(customer.getId())).thenReturn(new BigDecimal("1200.00"));

        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("800.00"), PaymentMethod.CASH);
        orderPaymentService.recordPayment(order.getId(), request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(new BigDecimal("1200.00"));
    }

    @Test
    void recordPayment_onCodOrder_syncsCustomerBalanceFromOrders() {
        Order order = unpaidOrder(new BigDecimal("2000.00"), PaymentMethod.COD);

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());
        when(orderRepository.sumOutstandingByCustomerId(customer.getId())).thenReturn(new BigDecimal("1200.00"));

        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("800.00"), PaymentMethod.CASH);
        orderPaymentService.recordPayment(order.getId(), request);

        assertThat(customer.getOutstandingBalance()).isEqualByComparingTo(new BigDecimal("1200.00"));
    }

    @Test
    void recordPayment_partialToFullOnPartiallyPaidCreditOrder_marksAsPaidAndReducesCustomerBalance() {
        customer = Customer.builder()
                .id(UUID.randomUUID())
                .outstandingBalance(new BigDecimal("1200.00"))
                .creditLimit(new BigDecimal("5000.00"))
                .customerCode("CU-654321")
                .build();

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .totalAmount(new BigDecimal("2000.00"))
                .paymentMethod(PaymentMethod.CREDIT)
                .paymentStatus(PaymentStatus.PARTIALLY_PAID)
                .paidAmount(new BigDecimal("800.00"))
                .outstandingBalance(new BigDecimal("1200.00"))
                .build();

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(new OrderResponse());
        when(orderRepository.sumOutstandingByCustomerId(customer.getId())).thenReturn(BigDecimal.ZERO);

        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("1200.00"), PaymentMethod.CASH);
        orderPaymentService.recordPayment(order.getId(), request);

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();

        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(saved.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(customer.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void recordPayment_amountExceedsOutstandingBalance_throws() {
        Order order = unpaidOrder(new BigDecimal("500.00"), PaymentMethod.COD);

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("600.00"), PaymentMethod.CASH);

        assertThatThrownBy(() -> orderPaymentService.recordPayment(order.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceed outstanding balance");
    }

    @Test
    void recordPayment_onAlreadyPaidOrder_throws() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .totalAmount(new BigDecimal("2000.00"))
                .paymentMethod(PaymentMethod.CASH)
                .paymentStatus(PaymentStatus.PAID)
                .paidAmount(new BigDecimal("2000.00"))
                .outstandingBalance(BigDecimal.ZERO)
                .build();

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("500.00"), PaymentMethod.CASH);

        assertThatThrownBy(() -> orderPaymentService.recordPayment(order.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already fully paid");
    }

    @Test
    void recordPayment_orderNotFound_throws() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.empty());

        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("500.00"), PaymentMethod.CASH);

        assertThatThrownBy(() -> orderPaymentService.recordPayment(orderId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void recordPayment_creditPaymentMethod_throws() {
        Order order = unpaidOrder(new BigDecimal("2000.00"), PaymentMethod.COD);

        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));

        RecordPaymentRequest request = new RecordPaymentRequest(new BigDecimal("500.00"), PaymentMethod.CREDIT);

        assertThatThrownBy(() -> orderPaymentService.recordPayment(order.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Credit cannot be used");
    }
}
