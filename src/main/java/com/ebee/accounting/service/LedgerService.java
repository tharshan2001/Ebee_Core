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

    // ── Customer ledger (accounts receivable) ───────────────────────────
    // balance = SUM(debit) - SUM(credit) = what the customer owes us.
    // See LedgerTransactionType for the posting rules. The advance wallet is NOT posted here.

    @Transactional
    public CustomerLedger recordCustomerInvoice(UUID customerId, UUID orderId, BigDecimal amount, String description, UUID recordedBy) {
        return postCustomer(customerId, orderId, null, LedgerTransactionType.INVOICE,
                amount, BigDecimal.ZERO, description, null, null, "ORDER", orderId, recordedBy);
    }

    /** Money received against an order or the account (cash, bank, cheque, or from the advance wallet). */
    @Transactional
    public CustomerLedger recordCustomerPayment(UUID customerId, UUID paymentId, BigDecimal amount, String description, UUID recordedBy) {
        return postCustomer(customerId, null, paymentId, LedgerTransactionType.PAYMENT,
                BigDecimal.ZERO, amount, description, null, null, "PAYMENT", paymentId, recordedBy);
    }

    /** Order value removed: cancellation, return, or permanently failed delivery. */
    @Transactional
    public CustomerLedger recordCustomerCreditNote(UUID customerId, UUID orderId, BigDecimal amount, String description, UUID recordedBy) {
        return postCustomer(customerId, orderId, null, LedgerTransactionType.CREDIT_NOTE,
                BigDecimal.ZERO, amount, description, null, null, "ORDER", orderId, recordedBy);
    }

    /** Overpaid money handed back to the customer in cash/bank. Debit: it reverses money we received. */
    @Transactional
    public CustomerLedger recordCustomerRefund(UUID customerId, UUID orderId, BigDecimal amount, String description, UUID recordedBy) {
        return postCustomer(customerId, orderId, null, LedgerTransactionType.REFUND,
                amount, BigDecimal.ZERO, description, null, null, "REFUND", orderId, recordedBy);
    }

    /** Overpaid money moved from the order into the customer's advance wallet. Debit, same as a refund. */
    @Transactional
    public CustomerLedger recordCustomerAdvanceTransfer(UUID customerId, UUID orderId, BigDecimal amount, String description, UUID recordedBy) {
        return postCustomer(customerId, orderId, null, LedgerTransactionType.ADVANCE_TRANSFER,
                amount, BigDecimal.ZERO, description, null, null, "ADVANCE", orderId, recordedBy);
    }

    /** Manual increase of what the customer owes. */
    @Transactional
    public CustomerLedger recordCustomerAdjustment(UUID customerId, BigDecimal debit, LocalDate effectiveDate, String description, String reference, UUID recordedBy) {
        return postCustomer(customerId, null, null, LedgerTransactionType.ADJUSTMENT,
                debit, BigDecimal.ZERO, description, effectiveDate, reference, "ADJUSTMENT", null, recordedBy);
    }

    /** Manual decrease of what the customer owes (e.g. reconciliation corrections). */
    @Transactional
    public CustomerLedger recordCustomerCreditAdjustment(UUID customerId, UUID orderId, BigDecimal credit, String description, UUID recordedBy) {
        return postCustomer(customerId, orderId, null, LedgerTransactionType.ADJUSTMENT,
                BigDecimal.ZERO, credit, description, null, null, "ADJUSTMENT", orderId, recordedBy);
    }

    private CustomerLedger postCustomer(UUID customerId, UUID orderId, UUID paymentId, LedgerTransactionType type,
                                        BigDecimal debit, BigDecimal credit, String description,
                                        LocalDate effectiveDate, String reference,
                                        String periodRefType, UUID periodRefId, UUID recordedBy) {
        if (debit.signum() < 0 || credit.signum() < 0) {
            throw new IllegalArgumentException("Ledger amounts must not be negative");
        }
        CustomerLedger entry = CustomerLedger.builder()
                .customerId(customerId)
                .orderId(orderId)
                .paymentId(paymentId)
                .transactionType(type.name())
                .debit(debit)
                .credit(credit)
                .description(description)
                .effectiveDate(effectiveDate)
                .reference(reference)
                .build();
        log.info("Customer ledger {}: customer={}, order={}, payment={}, debit={}, credit={}",
                type, customerId, orderId, paymentId, debit, credit);
        accountingPeriodService.requireOpenCurrentPeriod("CustomerLedger");
        CustomerLedger saved = customerLedgerRepository.save(entry);

        accountingPeriodService.recordPeriodTransaction(
                type.name(), debit, credit, periodRefId, periodRefType, description, recordedBy);

        return saved;
    }

    @Transactional(readOnly = true)
    public BigDecimal getCustomerBalance(UUID customerId) {
        return customerLedgerRepository.calculateBalance(customerId);
    }

    @Transactional
    public SupplierLedger recordSupplierInvoice(UUID supplierId, UUID purchaseOrderId, BigDecimal amount, String description, LocalDate effectiveDate, String reference, UUID recordedBy) {
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
        accountingPeriodService.requireOpenCurrentPeriod("SupplierLedger");
        SupplierLedger saved = supplierLedgerRepository.save(entry);

        accountingPeriodService.recordPeriodTransaction(
                LedgerTransactionType.INVOICE.name(),
                BigDecimal.ZERO,
                amount,
                purchaseOrderId,
                "SUPPLIER_INVOICE",
                description,
                recordedBy
        );

        return saved;
    }

    @Transactional
    public SupplierLedger recordSupplierPayment(UUID supplierId, UUID supplierPaymentId, BigDecimal amount, String description, LocalDate effectiveDate, String reference, UUID recordedBy) {
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
        accountingPeriodService.requireOpenCurrentPeriod("SupplierLedger");
        SupplierLedger saved = supplierLedgerRepository.save(entry);

        accountingPeriodService.recordPeriodTransaction(
                LedgerTransactionType.PAYMENT.name(),
                amount,
                BigDecimal.ZERO,
                supplierPaymentId,
                "SUPPLIER_PAYMENT",
                description,
                recordedBy
        );

        return saved;
    }

    @Transactional
    public SupplierLedger recordSupplierExpense(UUID supplierId, UUID expenseId, BigDecimal amount, String description, LocalDate effectiveDate, String reference, UUID recordedBy) {
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
        accountingPeriodService.requireOpenCurrentPeriod("SupplierLedger");
        SupplierLedger saved = supplierLedgerRepository.save(entry);

        accountingPeriodService.recordPeriodTransaction(
                LedgerTransactionType.DEBIT.name(),
                amount,
                BigDecimal.ZERO,
                expenseId,
                "SUPPLIER_EXPENSE",
                description,
                recordedBy
        );

        return saved;
    }

    @Transactional
    public SupplierLedger recordSupplierAdjustment(UUID supplierId, BigDecimal debit, LocalDate effectiveDate, String description, String reference, UUID recordedBy) {
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
        accountingPeriodService.requireOpenCurrentPeriod("SupplierLedger");
        SupplierLedger saved = supplierLedgerRepository.save(entry);

        accountingPeriodService.recordPeriodTransaction(
                LedgerTransactionType.ADJUSTMENT.name(),
                debit,
                BigDecimal.ZERO,
                null,
                "SUPPLIER_ADJUSTMENT",
                description,
                recordedBy
        );

        return saved;
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
    public AgentLedger recordAgentCommission(UUID agentId, UUID commissionId, BigDecimal amount, String description, UUID recordedBy) {
        AgentLedger entry = AgentLedger.builder()
                .agentId(agentId)
                .commissionId(commissionId)
                .transactionType(LedgerTransactionType.CREDIT.name())
                .debit(BigDecimal.ZERO)
                .credit(amount)
                .description(description)
                .build();
        log.info("Recording agent commission: agent={}, commission={}, amount={}", agentId, commissionId, amount);
        accountingPeriodService.requireOpenCurrentPeriod("AgentLedger");
        AgentLedger saved = agentLedgerRepository.save(entry);

        accountingPeriodService.recordPeriodTransaction(
                LedgerTransactionType.CREDIT.name(),
                BigDecimal.ZERO,
                amount,
                commissionId,
                "AGENT_COMMISSION",
                description,
                recordedBy
        );

        return saved;
    }

    @Transactional
    public AgentLedger recordAgentPayout(UUID agentId, UUID payoutId, BigDecimal amount, String description, UUID recordedBy) {
        AgentLedger entry = AgentLedger.builder()
                .agentId(agentId)
                .payoutId(payoutId)
                .transactionType(LedgerTransactionType.DEBIT.name())
                .debit(amount)
                .credit(BigDecimal.ZERO)
                .description(description)
                .build();
        log.info("Recording agent payout: agent={}, payout={}, amount={}", agentId, payoutId, amount);
        accountingPeriodService.requireOpenCurrentPeriod("AgentLedger");
        AgentLedger saved = agentLedgerRepository.save(entry);

        accountingPeriodService.recordPeriodTransaction(
                LedgerTransactionType.DEBIT.name(),
                amount,
                BigDecimal.ZERO,
                payoutId,
                "AGENT_PAYOUT",
                description,
                recordedBy
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public BigDecimal getAgentBalance(UUID agentId) {
        return agentLedgerRepository.calculateBalance(agentId);
    }
}
