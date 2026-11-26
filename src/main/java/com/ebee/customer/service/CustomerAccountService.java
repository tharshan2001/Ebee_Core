package com.ebee.customer.service;

import com.ebee.accounting.entity.CustomerLedger;
import com.ebee.accounting.repository.CustomerLedgerRepository;
import com.ebee.accounting.service.LedgerService;
import com.ebee.common.enums.District;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.result.Result;
import com.ebee.common.security.SecurityService;
import com.ebee.common.service.CodeGenerationService;
import com.ebee.common.util.PhoneUtils;
import com.ebee.customer.dto.AddressRequest;
import com.ebee.customer.dto.AddressResponse;
import com.ebee.customer.dto.AdvanceTransactionResponse;
import com.ebee.customer.dto.AdjustAdvanceRequest;
import com.ebee.customer.dto.AdjustOutstandingRequest;
import com.ebee.customer.dto.CreditLimitRequest;
import com.ebee.customer.dto.OutstandingAdjustmentResponse;
import com.ebee.customer.dto.CustomerPaymentResponse;
import com.ebee.customer.dto.CustomerRequest;
import com.ebee.customer.dto.CustomerResponse;
import com.ebee.customer.entity.Address;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.entity.CustomerAdvanceTransaction;
import com.ebee.customer.enums.AdvanceReferenceType;
import com.ebee.customer.enums.AdvanceTransactionType;
import com.ebee.customer.enums.DuesChangeType;
import com.ebee.customer.event.CustomerDuesChangedEvent;
import com.ebee.customer.repository.CustomerAdvanceTransactionRepository;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.notification.NotificationPort;
import com.ebee.order.dto.RecordPaymentRequest;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.PaymentStatus;
import com.ebee.order.repository.OrderRepository;
import com.ebee.order.service.OrderPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The money side of a customer: outstanding and advance balances (synced from the ledger), the
 * advance wallet, ledger views, manual adjustments and customer-level payments. CustomerService
 * delegates here, so callers keep using CustomerService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAccountService {

    private final CustomerRepository customerRepository;
    private final CustomerAdvanceTransactionRepository advanceTransactionRepository;
    private final OrderPaymentService orderPaymentService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LedgerService ledgerService;
    private final CustomerLedgerRepository customerLedgerRepository;
    private final SecurityService securityService;
    private final com.ebee.finance.service.MoneyAccountService moneyAccountService;

    public void syncCustomerOutstanding(Customer customer) {
        BigDecimal actual = customerLedgerRepository.calculateBalance(customer.getId());
        customer.setOutstandingBalance(actual);
    }

    public void syncAdvanceBalance(Customer customer) {
        BigDecimal actual = advanceTransactionRepository.sumAdvanceByCustomerId(customer.getId());
        customer.setAdvanceBalance(actual);
    }

    public void syncAllBalances(Customer customer) {
        syncCustomerOutstanding(customer);
        syncAdvanceBalance(customer);
    }

    public void syncAndPublishDuesChange(Customer customer, DuesChangeType changeType) {
        BigDecimal previousOutstanding = customer.getOutstandingBalance();
        BigDecimal previousAdvance = customer.getAdvanceBalance();
        syncCustomerOutstanding(customer);
        customerRepository.save(customer);
        publishDuesChange(customer, previousOutstanding, previousAdvance, changeType);
    }

    public void publishDuesChange(Customer customer, BigDecimal previousOutstanding,
                                  BigDecimal previousAdvance, DuesChangeType changeType) {
        eventPublisher.publishEvent(new CustomerDuesChangedEvent(
                this, customer,
                previousOutstanding, customer.getOutstandingBalance(),
                previousAdvance, customer.getAdvanceBalance(),
                changeType
        ));
    }

    public BigDecimal getAdvanceBalance(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));
        return customer.getAdvanceBalance();
    }

    @Transactional(readOnly = true)
    public Page<AdvanceTransactionResponse> getAdvanceHistory(UUID customerId, Pageable pageable) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", "id", customerId);
        }
        return advanceTransactionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(this::toAdvanceTransactionResponse);
    }

    @Transactional(readOnly = true)
    public Page<CustomerLedger> getCustomerLedger(UUID customerId, Pageable pageable) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", "id", customerId);
        }
        return customerLedgerRepository.findByCustomerId(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public List<CustomerLedger> getCustomerLedgerFiltered(UUID customerId, String entryType,
                                                          LocalDateTime startDate, LocalDateTime endDate) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", "id", customerId);
        }
        List<CustomerLedger> allEntries = customerLedgerRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        return allEntries.stream()
                .filter(e -> entryType == null || e.getTransactionType().equals(entryType))
                .filter(e -> startDate == null || !e.getCreatedAt().isBefore(startDate))
                .filter(e -> endDate == null || !e.getCreatedAt().isAfter(endDate))
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal getCustomerLedgerBalance(UUID customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", "id", customerId);
        }
        return customerLedgerRepository.calculateBalance(customerId);
    }

    @Transactional
    public CustomerAdvanceTransaction creditToAdvance(Customer customer, BigDecimal amount,
                                                       AdvanceReferenceType refType, UUID referenceId,
                                                       String description) {
        BigDecimal previousAdvance = customer.getAdvanceBalance();
        BigDecimal newBalance = previousAdvance.add(amount);

        CustomerAdvanceTransaction transaction = CustomerAdvanceTransaction.builder()
                .customer(customer)
                .amount(amount)
                .balanceAfter(newBalance)
                .transactionType(AdvanceTransactionType.ADVANCE_CREDIT)
                .referenceType(refType)
                .referenceId(referenceId)
                .description(description)
                .build();

        customer.setAdvanceBalance(newBalance);
        customerRepository.save(customer);

        // Wallet only: the advance balance is not part of the receivable ledger. When the credit
        // comes from an overpaid order, OrderSettlementService posts the matching ADVANCE_TRANSFER.

        publishDuesChange(customer, customer.getOutstandingBalance(), previousAdvance,
                DuesChangeType.ADVANCE_CREDITED);

        return advanceTransactionRepository.save(transaction);
    }

    @Transactional
    public CustomerAdvanceTransaction debitFromAdvance(Customer customer, BigDecimal amount,
                                                        AdvanceReferenceType refType, UUID referenceId,
                                                        String description) {
        if (customer.getAdvanceBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient advance balance");
        }

        BigDecimal previousAdvance = customer.getAdvanceBalance();
        BigDecimal newBalance = previousAdvance.subtract(amount);

        CustomerAdvanceTransaction transaction = CustomerAdvanceTransaction.builder()
                .customer(customer)
                .amount(amount.negate())
                .balanceAfter(newBalance)
                .transactionType(AdvanceTransactionType.ADVANCE_USED)
                .referenceType(refType)
                .referenceId(referenceId)
                .description(description)
                .build();

        customer.setAdvanceBalance(newBalance);
        customerRepository.save(customer);

        publishDuesChange(customer, customer.getOutstandingBalance(), previousAdvance,
                DuesChangeType.ADVANCE_USED);

        return advanceTransactionRepository.save(transaction);
    }

    @Transactional
    public CustomerAdvanceTransaction manualAdjustAdvance(UUID customerId, AdjustAdvanceRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        BigDecimal previousAdvance = customer.getAdvanceBalance();
        BigDecimal newBalance = previousAdvance.add(request.amount());

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Advance balance cannot be negative");
        }

        CustomerAdvanceTransaction transaction = CustomerAdvanceTransaction.builder()
                .customer(customer)
                .amount(request.amount())
                .balanceAfter(newBalance)
                .transactionType(AdvanceTransactionType.MANUAL_ADJUSTMENT)
                .referenceType(AdvanceReferenceType.ADMIN_ADJUSTMENT)
                .description(request.description())
                .build();

        customer.setAdvanceBalance(newBalance);
        customerRepository.save(customer);
        advanceTransactionRepository.save(transaction);
        // Wallet only: manual advance changes don't touch the receivable ledger.

        publishDuesChange(customer, customer.getOutstandingBalance(), previousAdvance,
                DuesChangeType.MANUAL_ADJUSTMENT);

        return transaction;
    }

    @Transactional
    public OutstandingAdjustmentResponse manualAdjustOutstanding(UUID customerId, AdjustOutstandingRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        BigDecimal previousOutstanding = customer.getOutstandingBalance();
        BigDecimal amountAdded = request.amount();
        BigDecimal newOutstanding = previousOutstanding.add(amountAdded);

        if (newOutstanding.compareTo(BigDecimal.ZERO) < 0) {
            newOutstanding = BigDecimal.ZERO;
        }

        customer.setOutstandingBalance(newOutstanding);
        customerRepository.save(customer);

        ledgerService.recordCustomerAdjustment(
                customerId,
                amountAdded,
                request.date(),
                request.description(),
                "Outstanding Adjustment",
                securityService.getCurrentUserId()
        );

        publishDuesChange(customer, previousOutstanding, customer.getAdvanceBalance(),
                DuesChangeType.MANUAL_ADJUSTMENT);

        return new OutstandingAdjustmentResponse(
                customerId,
                previousOutstanding,
                newOutstanding,
                amountAdded,
                request.date(),
                request.description()
        );
    }

    @Transactional
    public CustomerPaymentResponse recordCustomerPayment(UUID customerId, RecordPaymentRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        List<Order> unpaidOrders = orderRepository.findByCustomerIdAndPaymentStatusInWithDetails(
                customerId, List.of(PaymentStatus.UNPAID, PaymentStatus.PARTIALLY_PAID));

        BigDecimal remaining = request.amount();
        int settledCount = 0;

        if (!unpaidOrders.isEmpty()) {
            for (Order order : unpaidOrders) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal payAmount = remaining.min(order.getOutstandingBalance());
                orderPaymentService.recordPayment(order.getId(),
                        new RecordPaymentRequest(payAmount, request.paymentMethod(), request.chequeNumber(),
                                request.proofFileUrl(), request.accountId()), false);
                remaining = remaining.subtract(payAmount);
                settledCount++;
            }
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal outstanding = customerLedgerRepository.calculateBalance(customerId);
            if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal adjustmentPayment = remaining.min(outstanding);
                ledgerService.recordCustomerPayment(
                        customerId,
                        null,
                        adjustmentPayment,
                        "Payment for outstanding adjustment via " + request.paymentMethod(),
                        securityService.getCurrentUserId()
                );
                remaining = remaining.subtract(adjustmentPayment);
                moneyAccountService.recordMovement(com.ebee.finance.entity.AccountTransaction.Direction.IN, adjustmentPayment, request.paymentMethod().name(),
                        request.accountId(), com.ebee.finance.entity.AccountTransaction.Type.CUSTOMER_RECEIPT, "CUSTOMER", customerId,
                        "Account payment via " + request.paymentMethod());
            }
        }

        customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        syncCustomerOutstanding(customer);
        customer = customerRepository.save(customer);

        return new CustomerPaymentResponse(
                customer.getId(),
                customer.getFirstName() + " " + customer.getLastName(),
                request.amount().subtract(remaining),
                settledCount,
                customer.getOutstandingBalance()
        );
    }

    private AdvanceTransactionResponse toAdvanceTransactionResponse(CustomerAdvanceTransaction tx) {
        return new AdvanceTransactionResponse(
                tx.getId(),
                tx.getCustomer().getId(),
                tx.getOrder() != null ? tx.getOrder().getId() : null,
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getTransactionType(),
                tx.getReferenceType(),
                tx.getReferenceId(),
                tx.getDescription(),
                tx.getCreatedAt()
        );
    }

}
