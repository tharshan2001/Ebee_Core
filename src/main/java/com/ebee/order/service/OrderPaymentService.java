package com.ebee.order.service;

import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.customer.entity.Customer;
import com.ebee.order.dto.RecordPaymentRequest;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.dto.PaymentResponse;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.Payment;
import com.ebee.order.enums.PaymentMethod;
import com.ebee.order.enums.PaymentStatus;
import com.ebee.order.mapper.OrderMapper;
import com.ebee.order.repository.OrderRepository;
import com.ebee.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderMapper orderMapper;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;

    public void initPayment(Order order, PaymentMethod paymentMethod, BigDecimal paidAmount, String chequeNumber, String proofFileUrl) {
        if (paymentMethod == PaymentMethod.CHEQUE
                && (chequeNumber == null || chequeNumber.isBlank())) {
            throw new IllegalArgumentException("Cheque number is required for CHEQUE payment method");
        }

        if (paymentMethod == PaymentMethod.BANK_TRANSFER
                && paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) > 0
                && (proofFileUrl == null || proofFileUrl.isBlank())) {
            throw new IllegalArgumentException("Bank transfer proof (slip/receipt) is required when paying upfront");
        }

        if (paymentMethod == PaymentMethod.COD) {
            order.setPaymentMethod(PaymentMethod.COD);
            order.setPaymentStatus(PaymentStatus.UNPAID);
            order.setPaidAmount(BigDecimal.ZERO);
            order.setOutstandingBalance(order.getTotalAmount());
            updateCustomerOutstandingBalance(order.getCustomer(), order.getOutstandingBalance(), true);
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
            BigDecimal paid = paidAmount != null ? paidAmount : BigDecimal.ZERO;
            order.setPaymentMethod(paymentMethod);
            if (paid.compareTo(BigDecimal.ZERO) <= 0) {
                order.setPaymentStatus(PaymentStatus.UNPAID);
                order.setPaidAmount(BigDecimal.ZERO);
                order.setOutstandingBalance(order.getTotalAmount());
                updateCustomerOutstandingBalance(order.getCustomer(), order.getOutstandingBalance(), true);
            } else if (paid.compareTo(order.getTotalAmount()) >= 0) {
                order.setPaymentStatus(PaymentStatus.PAID);
                order.setPaidAmount(order.getTotalAmount());
                order.setOutstandingBalance(BigDecimal.ZERO);
                order.setPaidAt(LocalDateTime.now());
            } else {
                order.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
                order.setPaidAmount(paid);
                order.setOutstandingBalance(order.getTotalAmount().subtract(paid));
                order.setPaidAt(LocalDateTime.now());
                updateCustomerOutstandingBalance(order.getCustomer(), order.getOutstandingBalance(), true);
            }
        }
        // NOTE: syncCustomerOutstanding is NOT called here because initPayment
        // runs BEFORE orderRepository.save() in OrderServiceImpl.createOrder().
        // The sync is called after save in createOrder() instead.
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

        if (request.paymentMethod() == PaymentMethod.CHEQUE
                && (request.chequeNumber() == null || request.chequeNumber().isBlank())) {
            throw new IllegalArgumentException("Cheque number is required when paying by cheque");
        }

        if (request.paymentMethod() == PaymentMethod.BANK_TRANSFER
                && (request.proofFileUrl() == null || request.proofFileUrl().isBlank())) {
            throw new IllegalArgumentException("Bank transfer proof (slip/receipt) is required");
        }

        boolean isCreditOrder = order.getPaymentMethod() == PaymentMethod.CREDIT;
        boolean isCodOrder = order.getPaymentMethod() == PaymentMethod.COD;

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

        // Insert payment record
        UUID currentUserId = securityService.getCurrentUserId();
        String currentUserName = securityService.getCurrentUserEmail();
        Payment payment = Payment.builder()
                .orderId(order.getId())
                .amount(request.amount())
                .paymentMethod(request.paymentMethod())
                .chequeNumber(request.chequeNumber())
                .proofFileUrl(request.proofFileUrl())
                .recordedBy(currentUserId)
                .recordedByName(currentUserName)
                .build();
        paymentRepository.save(payment);

        if (isCreditOrder || isCodOrder) {
            updateCustomerOutstandingBalance(order.getCustomer(), request.amount(), false);
            syncCustomerOutstanding(order.getCustomer());
        }

        auditLogService.record(AuditAction.UPDATE, "Order", order.getId(),
                "Payment recorded for order " + order.getOrderNumber()
                + ": " + request.amount() + " via " + request.paymentMethod());

        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentHistory(UUID orderId) {
        return paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(p -> new PaymentResponse(
                        p.getId(),
                        p.getAmount(),
                        p.getPaymentMethod(),
                        p.getChequeNumber(),
                        p.getProofFileUrl(),
                        p.getRecordedByName(),
                        p.getCreatedAt()))
                .toList();
    }

    public void handlePaymentRefund(Order order) {
        BigDecimal refundAmount = order.getPaidAmount();
        BigDecimal remainingOutstanding = order.getOutstandingBalance();
        boolean hasRefund = refundAmount.compareTo(BigDecimal.ZERO) > 0;

        if (remainingOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            updateCustomerOutstandingBalance(order.getCustomer(), remainingOutstanding, false);
        }

        if (hasRefund) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            order.setPaidAmount(BigDecimal.ZERO);
            order.setOutstandingBalance(BigDecimal.ZERO);
        } else {
            order.setOutstandingBalance(BigDecimal.ZERO);
        }
        syncCustomerOutstanding(order.getCustomer());
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

    /**
     * Safety net: recalculates customer.outstandingBalance from the source of truth
     * (sum of unpaid/partially-paid order outstanding balances). Called after every
     * payment mutation to prevent incremental counter drift.
     */
    void syncCustomerOutstanding(Customer customer) {
        if (customer == null) return;
        BigDecimal actual = orderRepository.sumOutstandingByCustomerId(customer.getId());
        if (customer.getOutstandingBalance().compareTo(actual) != 0) {
            log.warn("Auto-correcting customer {} outstanding: recorded={} actual={}",
                    customer.getId(), customer.getOutstandingBalance(), actual);
            customer.setOutstandingBalance(actual);
        }
    }
}
