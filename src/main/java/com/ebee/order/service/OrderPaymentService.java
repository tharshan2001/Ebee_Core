package com.ebee.order.service;

import com.ebee.accounting.service.LedgerService;
import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.entity.CustomerAdvanceTransaction;
import com.ebee.customer.enums.AdvanceReferenceType;
import com.ebee.customer.enums.AdvanceTransactionType;
import com.ebee.customer.enums.DuesChangeType;
import com.ebee.customer.event.CustomerDuesChangedEvent;
import com.ebee.customer.repository.CustomerAdvanceTransactionRepository;
import com.ebee.customer.repository.CustomerRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
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
    private final CustomerAdvanceTransactionRepository advanceTransactionRepository;
    private final CustomerRepository customerRepository;
    private final OrderMapper orderMapper;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;
    private final LedgerService ledgerService;
    private final com.ebee.finance.service.MoneyAccountService moneyAccountService;
    private com.ebee.customer.service.CustomerService customerService;

    @Autowired
    @Lazy
    public void setCustomerService(com.ebee.customer.service.CustomerService customerService) {
        this.customerService = customerService;
    }

    public void initPayment(Order order, PaymentMethod paymentMethod, BigDecimal paidAmount, String chequeNumber, String proofFileUrl) {
        if (paymentMethod != PaymentMethod.COD && paidAmount != null
                && paidAmount.compareTo(order.getTotalAmount()) > 0) {
            throw new IllegalArgumentException("Paid amount " + paidAmount
                    + " is more than the order total of " + order.getTotalAmount());
        }
        if (paymentMethod == PaymentMethod.CHEQUE
                && (chequeNumber == null || chequeNumber.isBlank())) {
            throw new IllegalArgumentException("Cheque number is required for CHEQUE payment method");
        }

        if ((paymentMethod == PaymentMethod.BANK_TRANSFER || paymentMethod == PaymentMethod.CARD_PAYMENT)
                && paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) > 0
                && (proofFileUrl == null || proofFileUrl.isBlank())) {
            throw new IllegalArgumentException("Payment proof (slip/receipt) is required for " + paymentMethod + " when paying upfront");
        }

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
        } else {
            BigDecimal paid = paidAmount != null ? paidAmount : BigDecimal.ZERO;
            order.setPaymentMethod(paymentMethod);
            if (paid.compareTo(BigDecimal.ZERO) <= 0) {
                order.setPaymentStatus(PaymentStatus.UNPAID);
                order.setPaidAmount(BigDecimal.ZERO);
                order.setOutstandingBalance(order.getTotalAmount());
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
            }
        }
        if (order.getCustomer() != null
                && order.getCustomer().getCreditLimit().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal projectedOutstanding = order.getCustomer().getOutstandingBalance()
                    .add(order.getOutstandingBalance());
            if (projectedOutstanding.compareTo(order.getCustomer().getCreditLimit()) > 0) {
                throw new IllegalArgumentException(
                        "Order would exceed customer credit limit of " + order.getCustomer().getCreditLimit()
                                + ". Projected outstanding: " + projectedOutstanding);
            }
        }
    }

    @Transactional
    public OrderResponse recordPayment(UUID id, RecordPaymentRequest request) {
        return recordPayment(id, request, true);
    }

    @Transactional
    public OrderResponse recordPayment(UUID id, RecordPaymentRequest request, boolean syncCustomer) {
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

        if ((request.paymentMethod() == PaymentMethod.BANK_TRANSFER || request.paymentMethod() == PaymentMethod.CARD_PAYMENT)
                && (request.proofFileUrl() == null || request.proofFileUrl().isBlank())) {
            throw new IllegalArgumentException("Payment proof (slip/receipt) is required for " + request.paymentMethod());
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
        moneyAccountService.recordMovement(com.ebee.finance.entity.AccountTransaction.Direction.IN, request.amount(), request.paymentMethod().name(),
                request.accountId(), com.ebee.finance.entity.AccountTransaction.Type.CUSTOMER_RECEIPT, "PAYMENT", payment.getId(),
                "Payment for Order " + order.getOrderNumber() + " via " + request.paymentMethod());

        ledgerService.recordCustomerPayment(
                order.getCustomer().getId(),
                payment.getId(),
                request.amount(),
                "Payment for Order " + order.getOrderNumber() + " via " + request.paymentMethod(),
                currentUserId
        );

        if (syncCustomer) {
            syncCustomerOutstanding(order.getCustomer());
            customerRepository.save(order.getCustomer());
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

    @Transactional
    public void useAdvanceForOrder(Order order, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Customer customer = order.getCustomer();
        if (customer.getAdvanceBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient advance balance");
        }
        if (amount.compareTo(order.getOutstandingBalance()) > 0) {
            throw new IllegalArgumentException("Advance amount " + amount
                    + " exceeds the order's outstanding balance of " + order.getOutstandingBalance());
        }

        BigDecimal previousOutstanding = customer.getOutstandingBalance();
        BigDecimal previousAdvance = customer.getAdvanceBalance();

        BigDecimal newAdvance = previousAdvance.subtract(amount);
        customer.setAdvanceBalance(newAdvance);

        BigDecimal newPaidAmount = order.getPaidAmount().add(amount);
        if (newPaidAmount.compareTo(order.getTotalAmount()) >= 0) {
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setPaidAmount(order.getTotalAmount());
            order.setOutstandingBalance(BigDecimal.ZERO);
        } else {
            order.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
            order.setPaidAmount(newPaidAmount);
            order.setOutstandingBalance(order.getTotalAmount().subtract(newPaidAmount));
        }
        order.setPaidAt(LocalDateTime.now());

        orderRepository.save(order);

        UUID currentUserId = securityService.getCurrentUserId();
        String currentUserName = securityService.getCurrentUserEmail();
        Payment payment = Payment.builder()
                .orderId(order.getId())
                .amount(amount)
                .paymentMethod(PaymentMethod.CREDIT)
                .recordedBy(currentUserId)
                .recordedByName(currentUserName)
                .build();
        paymentRepository.save(payment);

        ledgerService.recordCustomerPayment(
                customer.getId(),
                payment.getId(),
                amount,
                "Advance payment for Order " + order.getOrderNumber(),
                currentUserId
        );

        CustomerAdvanceTransaction transaction = CustomerAdvanceTransaction.builder()
                .customer(customer)
                .order(order)
                .amount(amount.negate())
                .balanceAfter(newAdvance)
                .transactionType(AdvanceTransactionType.ADVANCE_USED)
                .referenceType(AdvanceReferenceType.PAYMENT)
                .referenceId(order.getId())
                .description("Used advance for order #" + order.getOrderNumber())
                .build();
        advanceTransactionRepository.save(transaction);

        syncCustomerOutstanding(customer);
        customerRepository.save(customer);
        publishDuesChange(customer, previousOutstanding, previousAdvance, DuesChangeType.ADVANCE_USED);
    }

    private void publishDuesChange(Customer customer, BigDecimal previousOutstanding,
                                   BigDecimal previousAdvance, DuesChangeType changeType) {
        eventPublisher.publishEvent(new CustomerDuesChangedEvent(
                this, customer,
                previousOutstanding, customer.getOutstandingBalance(),
                previousAdvance, customer.getAdvanceBalance(),
                changeType
        ));
    }

    void syncCustomerOutstanding(Customer customer) {
        if (customer == null) return;
        customerService.syncCustomerOutstanding(customer);
    }

    public List<PaymentResponse> getPaymentsByOrderId(UUID orderId) {
        List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        return payments.stream()
                .map(p -> new PaymentResponse(
                        p.getId(),
                        p.getAmount(),
                        p.getPaymentMethod(),
                        p.getChequeNumber(),
                        p.getProofFileUrl(),
                        p.getRecordedByName(),
                        p.getCreatedAt()
                ))
                .toList();
    }
}
