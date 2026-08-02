package com.ebee.accounting.service;

import com.ebee.accounting.entity.AgentLedger;
import com.ebee.accounting.entity.CustomerLedger;
import com.ebee.accounting.entity.SupplierLedger;
import com.ebee.accounting.enums.LedgerTransactionType;
import com.ebee.accounting.enums.LedgerType;
import com.ebee.accounting.repository.AgentLedgerRepository;
import com.ebee.accounting.repository.CustomerLedgerRepository;
import com.ebee.accounting.repository.SupplierLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final CustomerLedgerRepository customerLedgerRepository;
    private final SupplierLedgerRepository supplierLedgerRepository;
    private final AgentLedgerRepository agentLedgerRepository;
    private final AccountingPeriodService accountingPeriodService;

    @Transactional
    public CustomerLedger recordCustomerInvoice(UUID customerId, UUID orderId, BigDecimal amount, String description, UUID recordedBy) {
        CustomerLedger entry = CustomerLedger.builder()
                .customerId(customerId)
                .orderId(orderId)
                .transactionType(LedgerTransactionType.INVOICE.name())
                .debit(amount)
                .credit(BigDecimal.ZERO)
                .description(description)
                .build();
        log.info("Recording customer invoice: customer={}, order={}, amount={}", customerId, orderId, amount);
        CustomerLedger saved = customerLedgerRepository.save(entry);

        accountingPeriodService.recordPeriodTransaction(
                LedgerTransactionType.INVOICE.name(),
                amount,
                BigDecimal.ZERO,
                orderId,
                "ORDER",
                description,
                recordedBy
        );

        return saved;
    }

    @Transactional
    public CustomerLedger recordCustomerPayment(UUID customerId, UUID paymentId, BigDecimal amount, String description, UUID recordedBy) {
        CustomerLedger entry = CustomerLedger.builder()
                .customerId(customerId)
                .paymentId(paymentId)
                .transactionType(LedgerTransactionType.PAYMENT.name())
                .debit(BigDecimal.ZERO)
                .credit(amount)
                .description(description)
                .build();
        log.info("Recording customer payment: customer={}, payment={}, amount={}", customerId, paymentId, amount);
        CustomerLedger saved = customerLedgerRepository.save(entry);

        accountingPeriodService.recordPeriodTransaction(
                LedgerTransactionType.PAYMENT.name(),
                BigDecimal.ZERO,
                amount,
                paymentId,
                "PAYMENT",
                description,
                recordedBy
        );

        return saved;
    }

    @Transactional
    public CustomerLedger recordCustomerRefund(UUID customerId, UUID returnId, BigDecimal amount, String description, UUID recordedBy) {
        CustomerLedger entry = CustomerLedger.builder()
                .customerId(customerId)
                .returnId(returnId)
                .transactionType(LedgerTransactionType.REFUND.name())
                .debit(BigDecimal.ZERO)
                .credit(amount)
                .description(description)
                .build();
        log.info("Recording customer refund: customer={}, return={}, amount={}", customerId, returnId, amount);
        CustomerLedger saved = customerLedgerRepository.save(entry);

        accountingPeriodService.recordPeriodTransaction(
                LedgerTransactionType.REFUND.name(),
                amount,
                BigDecimal.ZERO,
                returnId,
                "RETURN",
                description,
                recordedBy
        );

        return saved;
    }

    @Transactional
    public CustomerLedger recordCustomerAdvance(UUID customerId, UUID paymentId, BigDecimal amount, String description, UUID recordedBy) {
        CustomerLedger entry = CustomerLedger.builder()
                .customerId(customerId)
                .paymentId(paymentId)
                .transactionType(LedgerTransactionType.ADVANCE.name())
                .debit(BigDecimal.ZERO)
                .credit(amount)
                .description(description)
                .build();
        log.info("Recording customer advance: customer={}, payment={}, amount={}", customerId, paymentId, amount);
        CustomerLedger saved = customerLedgerRepository.save(entry);

        accountingPeriodService.recordPeriodTransaction(
                LedgerTransactionType.ADVANCE.name(),
                BigDecimal.ZERO,
                amount,
                paymentId,
                "ADVANCE",
                description,
                recordedBy
        );

        return saved;
    }

    @Transactional
    public CustomerLedger recordCustomerAdjustment(UUID customerId, BigDecimal debit, LocalDate effectiveDate, String description, String reference, UUID recordedBy) {
        CustomerLedger entry = CustomerLedger.builder()
                .customerId(customerId)
                .transactionType(LedgerTransactionType.ADJUSTMENT.name())
                .debit(debit)
                .credit(BigDecimal.ZERO)
                .effectiveDate(effectiveDate)
                .description(description)
                .reference(reference)
                .build();
        log.info("Recording customer adjustment: customer={}, debit={}, effectiveDate={}, description={}", customerId, debit, effectiveDate, description);
        return customerLedgerRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public BigDecimal getCustomerBalance(UUID customerId) {
        return customerLedgerRepository.calculateBalance(customerId);
    }

    @Transactional
    public SupplierLedger recordSupplierInvoice(UUID supplierId, UUID purchaseOrderId, BigDecimal amount, String description, LocalDate effectiveDate, String reference) {
        SupplierLedger entry = SupplierLedger.builder()
                .supplierId(supplierId)
                .purchaseOrderId(purchaseOrderId)
                .transactionType(LedgerTransactionType.INVOICE.name())
                .debit(BigDecimal.ZERO)
                .credit(amount)
                .description(description)
                .effectiveDate(effectiveDate)
                .reference(reference)
                .build();
        log.info("Recording supplier invoice: supplier={}, po={}, amount={}", supplierId, purchaseOrderId, amount);
        return supplierLedgerRepository.save(entry);
    }

    @Transactional
    public SupplierLedger recordSupplierPayment(UUID supplierId, UUID supplierPaymentId, BigDecimal amount, String description, LocalDate effectiveDate, String reference) {
        SupplierLedger entry = SupplierLedger.builder()
                .supplierId(supplierId)
                .supplierPaymentId(supplierPaymentId)
                .transactionType(LedgerTransactionType.PAYMENT.name())
                .debit(amount)
                .credit(BigDecimal.ZERO)
                .description(description)
                .effectiveDate(effectiveDate)
                .reference(reference)
                .build();
        log.info("Recording supplier payment: supplier={}, payment={}, amount={}", supplierId, supplierPaymentId, amount);
        return supplierLedgerRepository.save(entry);
    }

    @Transactional
    public SupplierLedger recordSupplierExpense(UUID supplierId, UUID expenseId, BigDecimal amount, String description, LocalDate effectiveDate, String reference) {
        SupplierLedger entry = SupplierLedger.builder()
                .supplierId(supplierId)
                .expenseId(expenseId)
                .transactionType(LedgerTransactionType.DEBIT.name())
                .debit(amount)
                .credit(BigDecimal.ZERO)
                .description(description)
                .effectiveDate(effectiveDate)
                .reference(reference)
                .build();
        log.info("Recording supplier expense: supplier={}, expense={}, amount={}", supplierId, expenseId, amount);
        return supplierLedgerRepository.save(entry);
    }

    @Transactional
    public SupplierLedger recordSupplierAdjustment(UUID supplierId, BigDecimal debit, LocalDate effectiveDate, String description, String reference) {
        SupplierLedger entry = SupplierLedger.builder()
                .supplierId(supplierId)
                .transactionType(LedgerTransactionType.ADJUSTMENT.name())
                .debit(debit)
                .credit(BigDecimal.ZERO)
                .effectiveDate(effectiveDate)
                .description(description)
                .reference(reference)
                .build();
        log.info("Recording supplier adjustment: supplier={}, debit={}, effectiveDate={}, description={}", supplierId, debit, effectiveDate, description);
        return supplierLedgerRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public BigDecimal getSupplierBalance(UUID supplierId) {
        return supplierLedgerRepository.calculateBalance(supplierId);
    }

    @Transactional(readOnly = true)
    public List<SupplierLedger> getSupplierLedgerEntries(UUID supplierId) {
        return supplierLedgerRepository.findBySupplierIdOrderByCreatedAtDesc(supplierId);
    }

    @Transactional
    public AgentLedger recordAgentCommission(UUID agentId, UUID commissionId, BigDecimal amount, String description) {
        AgentLedger entry = AgentLedger.builder()
                .agentId(agentId)
                .commissionId(commissionId)
                .transactionType(LedgerTransactionType.CREDIT.name())
                .debit(BigDecimal.ZERO)
                .credit(amount)
                .description(description)
                .build();
        log.info("Recording agent commission: agent={}, commission={}, amount={}", agentId, commissionId, amount);
        return agentLedgerRepository.save(entry);
    }

    @Transactional
    public AgentLedger recordAgentPayout(UUID agentId, UUID payoutId, BigDecimal amount, String description) {
        AgentLedger entry = AgentLedger.builder()
                .agentId(agentId)
                .payoutId(payoutId)
                .transactionType(LedgerTransactionType.DEBIT.name())
                .debit(amount)
                .credit(BigDecimal.ZERO)
                .description(description)
                .build();
        log.info("Recording agent payout: agent={}, payout={}, amount={}", agentId, payoutId, amount);
        return agentLedgerRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public BigDecimal getAgentBalance(UUID agentId) {
        return agentLedgerRepository.calculateBalance(agentId);
    }
}
