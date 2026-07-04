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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final AuditLogService auditLogService;

    public void initPayment(Order order, PaymentMethod paymentMethod, BigDecimal paidAmount) {
        if (paymentMethod == PaymentMethod.COD) {
            order.setPaymentMethod(PaymentMethod.COD);
            order.setPaymentStatus(PaymentStatus.UNPAID);
            order.setPaidAmount(BigDecimal.ZERO);
            order.setOutstandingBalance(order.getTotalAmount());
        } else if (paymentMethod == PaymentMethod.CREDIT) {
            order.setPaymentMethod(PaymentMethod.CREDIT);
            BigDecimal paid = paidAmount != null ? paidAmount : BigDecimal.ZERO;
            order.setPaidAmount(paid);
            if (paid.compareTo(BigDecimal.ZERO) <= 0) {
                order.setPaymentStatus(PaymentStatus.UNPAID);
                order.setOutstandingBalance(order.getTotalAmount());
            } else if (paid.compareTo(order.getTotalAmount()) >= 0) {
                order.setPaymentStatus(PaymentStatus.PAID);
                order.setPaidAmount(order.getTotalAmount());
                order.setOutstandingBalance(BigDecimal.ZERO);
                order.setPaidAt(LocalDateTime.now());
            } else {
                order.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
                order.setOutstandingBalance(order.getTotalAmount().subtract(paid));
                order.setPaidAt(LocalDateTime.now());
            }
            updateCustomerOutstandingBalance(order.getCustomer(), order.getOutstandingBalance(), true);
        } else {
            order.setPaymentMethod(paymentMethod);
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setPaidAmount(order.getTotalAmount());
            order.setOutstandingBalance(BigDecimal.ZERO);
            order.setPaidAt(LocalDateTime.now());
        }
    }

    @Transactional
    public OrderResponse recordPayment(UUID id, RecordPaymentRequest request) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException("Order is already fully paid");
        }

        if (request.amount().compareTo(order.getOutstandingBalance()) > 0) {
            throw new IllegalArgumentException(
                    "Payment amount cannot exceed outstanding balance of " + order.getOutstandingBalance());
        }

        if (request.paymentMethod() == PaymentMethod.CREDIT) {
            throw new IllegalArgumentException("Credit cannot be used to record a payment");
        }

        boolean isCreditOrder = order.getPaymentMethod() == PaymentMethod.CREDIT;

        BigDecimal newPaidAmount = order.getPaidAmount().add(request.amount());

        if (newPaidAmount.compareTo(order.getTotalAmount()) >= 0) {
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setPaidAmount(order.getTotalAmount());
            order.setOutstandingBalance(BigDecimal.ZERO);
        } else {
            order.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
            order.setPaidAmount(newPaidAmount);
            order.setOutstandingBalance(order.getTotalAmount().subtract(newPaidAmount));
        }
        order.setPaymentMethod(request.paymentMethod());
        order.setPaidAt(LocalDateTime.now());

        order = orderRepository.save(order);

        if (isCreditOrder) {
            updateCustomerOutstandingBalance(order.getCustomer(), request.amount(), false);
        }

        auditLogService.record(AuditAction.UPDATE, "Order", order.getId(),
                "Payment recorded for order " + order.getOrderNumber()
                + ": " + request.amount() + " via " + request.paymentMethod());

        return orderMapper.toResponse(order);
    }

    public void handlePaymentRefund(Order order) {
        BigDecimal refundAmount = order.getPaidAmount();
        boolean hasRefund = refundAmount.compareTo(BigDecimal.ZERO) > 0;

        if (order.getPaymentMethod() == PaymentMethod.CREDIT) {
            updateCustomerOutstandingBalance(order.getCustomer(), order.getOutstandingBalance(), false);
        }

        if (hasRefund) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            order.setPaidAmount(BigDecimal.ZERO);
            order.setOutstandingBalance(BigDecimal.ZERO);
        } else {
            order.setOutstandingBalance(BigDecimal.ZERO);
        }
    }

    private void updateCustomerOutstandingBalance(Customer customer, BigDecimal outstandingBalance, boolean isAdd) {
        if (customer == null) return;
        if (isAdd) {
            customer.setOutstandingBalance(customer.getOutstandingBalance().add(outstandingBalance));
        } else {
            customer.setOutstandingBalance(customer.getOutstandingBalance().subtract(outstandingBalance));
            if (customer.getOutstandingBalance().compareTo(BigDecimal.ZERO) < 0) {
                customer.setOutstandingBalance(BigDecimal.ZERO);
            }
        }
    }
}
