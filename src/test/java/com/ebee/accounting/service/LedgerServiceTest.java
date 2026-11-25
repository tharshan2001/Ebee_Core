package com.ebee.accounting.service;

import com.ebee.accounting.entity.AgentLedger;
import com.ebee.accounting.entity.CustomerLedger;
import com.ebee.accounting.entity.SupplierLedger;
import com.ebee.accounting.enums.LedgerTransactionType;
import com.ebee.accounting.repository.AgentLedgerRepository;
import com.ebee.accounting.repository.CustomerLedgerRepository;
import com.ebee.accounting.repository.SupplierLedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private CustomerLedgerRepository customerLedgerRepository;

    @Mock
    private SupplierLedgerRepository supplierLedgerRepository;

    @Mock
    private AgentLedgerRepository agentLedgerRepository;

    @Mock
    private AccountingPeriodService accountingPeriodService;

    @InjectMocks
    private LedgerService ledgerService;

    @Captor
    private ArgumentCaptor<CustomerLedger> customerLedgerCaptor;

    @Captor
    private ArgumentCaptor<SupplierLedger> supplierLedgerCaptor;

    @Captor
    private ArgumentCaptor<AgentLedger> agentLedgerCaptor;

    @Test
    void recordCustomerInvoice_createsInvoiceEntry() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID recordedBy = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("5000.00");
        String description = "Order ORD-001";

        CustomerLedger result = ledgerService.recordCustomerInvoice(customerId, orderId, amount, description, recordedBy);

        verify(customerLedgerRepository).save(customerLedgerCaptor.capture());
        CustomerLedger saved = customerLedgerCaptor.getValue();

        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getTransactionType()).isEqualTo(LedgerTransactionType.INVOICE.name());
        assertThat(saved.getDebit()).isEqualByComparingTo(amount);
        assertThat(saved.getCredit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getDescription()).isEqualTo(description);
    }

    @Test
    void recordCustomerPayment_createsPaymentEntry() {
        UUID customerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID recordedBy = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("2000.00");
        String description = "Payment via CASH";

        CustomerLedger result = ledgerService.recordCustomerPayment(customerId, paymentId, amount, description, recordedBy);

        verify(customerLedgerRepository).save(customerLedgerCaptor.capture());
        CustomerLedger saved = customerLedgerCaptor.getValue();

        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getPaymentId()).isEqualTo(paymentId);
        assertThat(saved.getTransactionType()).isEqualTo(LedgerTransactionType.PAYMENT.name());
        assertThat(saved.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getCredit()).isEqualByComparingTo(amount);
        assertThat(saved.getDescription()).isEqualTo(description);
    }

    @Test
    void recordCustomerRefund_createsRefundEntry() {
        UUID customerId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        UUID recordedBy = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1500.00");
        String description = "Return for Order ORD-001";

        CustomerLedger result = ledgerService.recordCustomerRefund(customerId, returnId, amount, description, recordedBy);

        verify(customerLedgerRepository).save(customerLedgerCaptor.capture());
        CustomerLedger saved = customerLedgerCaptor.getValue();

        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        // Refunds are debits tied to the order (returnId param is the order id).
        assertThat(saved.getOrderId()).isEqualTo(returnId);
        assertThat(saved.getTransactionType()).isEqualTo(LedgerTransactionType.REFUND.name());
        assertThat(saved.getDebit()).isEqualByComparingTo(amount);
        assertThat(saved.getCredit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getDescription()).isEqualTo(description);
    }

    @Test
    void recordCustomerAdvanceTransfer_isDebitTiedToOrder() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID recordedBy = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1000.00");

        ledgerService.recordCustomerAdvanceTransfer(customerId, orderId, amount, "Moved to advance", recordedBy);

        verify(customerLedgerRepository).save(customerLedgerCaptor.capture());
        CustomerLedger saved = customerLedgerCaptor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getTransactionType()).isEqualTo(LedgerTransactionType.ADVANCE_TRANSFER.name());
        assertThat(saved.getDebit()).isEqualByComparingTo(amount);
        assertThat(saved.getCredit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void recordCustomerRefund_isDebitTiedToOrder() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("500.00");

        ledgerService.recordCustomerRefund(customerId, orderId, amount, "Refund", UUID.randomUUID());

        verify(customerLedgerRepository).save(customerLedgerCaptor.capture());
        CustomerLedger saved = customerLedgerCaptor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getTransactionType()).isEqualTo(LedgerTransactionType.REFUND.name());
        assertThat(saved.getDebit()).isEqualByComparingTo(amount);
        assertThat(saved.getCredit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void recordCustomerCreditNote_isCreditTiedToOrder() {
        UUID orderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("750.00");

        ledgerService.recordCustomerCreditNote(UUID.randomUUID(), orderId, amount, "Cancelled", UUID.randomUUID());

        verify(customerLedgerRepository).save(customerLedgerCaptor.capture());
        CustomerLedger saved = customerLedgerCaptor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getTransactionType()).isEqualTo(LedgerTransactionType.CREDIT_NOTE.name());
        assertThat(saved.getCredit()).isEqualByComparingTo(amount);
        assertThat(saved.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void recordSupplierInvoice_createsInvoiceEntry() {
        UUID supplierId = UUID.randomUUID();
        UUID poId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("8000.00");
        String description = "PO-001";
        LocalDate effectiveDate = LocalDate.now();
        String reference = "PO Invoice: PO-001";

        SupplierLedger result = ledgerService.recordSupplierInvoice(supplierId, poId, amount, description, effectiveDate, reference, UUID.randomUUID());

        verify(supplierLedgerRepository).save(supplierLedgerCaptor.capture());
        SupplierLedger saved = supplierLedgerCaptor.getValue();

        assertThat(saved.getSupplierId()).isEqualTo(supplierId);
        assertThat(saved.getPurchaseOrderId()).isEqualTo(poId);
        assertThat(saved.getTransactionType()).isEqualTo(LedgerTransactionType.INVOICE.name());
        assertThat(saved.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getCredit()).isEqualTo(amount);
        assertThat(saved.getDescription()).isEqualTo(description);
        assertThat(saved.getEffectiveDate()).isEqualTo(effectiveDate);
        assertThat(saved.getReference()).isEqualTo(reference);
    }

    @Test
    void recordSupplierPayment_createsPaymentEntry() {
        UUID supplierId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("3000.00");
        String description = "Payment to supplier";
        LocalDate effectiveDate = LocalDate.now();
        String reference = "PO Payment: PO-001";

        SupplierLedger result = ledgerService.recordSupplierPayment(supplierId, paymentId, amount, description, effectiveDate, reference, UUID.randomUUID());

        verify(supplierLedgerRepository).save(supplierLedgerCaptor.capture());
        SupplierLedger saved = supplierLedgerCaptor.getValue();

        assertThat(saved.getSupplierId()).isEqualTo(supplierId);
        assertThat(saved.getSupplierPaymentId()).isEqualTo(paymentId);
        assertThat(saved.getTransactionType()).isEqualTo(LedgerTransactionType.PAYMENT.name());
        assertThat(saved.getDebit()).isEqualTo(amount);
        assertThat(saved.getCredit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getDescription()).isEqualTo(description);
        assertThat(saved.getEffectiveDate()).isEqualTo(effectiveDate);
        assertThat(saved.getReference()).isEqualTo(reference);
    }

    @Test
    void recordAgentCommission_createsCommissionEntry() {
        UUID agentId = UUID.randomUUID();
        UUID commissionId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("500.00");
        String description = "Commission for ORD-001";

        AgentLedger result = ledgerService.recordAgentCommission(agentId, commissionId, amount, description, UUID.randomUUID());

        verify(agentLedgerRepository).save(agentLedgerCaptor.capture());
        AgentLedger saved = agentLedgerCaptor.getValue();

        assertThat(saved.getAgentId()).isEqualTo(agentId);
        assertThat(saved.getCommissionId()).isEqualTo(commissionId);
        assertThat(saved.getTransactionType()).isEqualTo(LedgerTransactionType.CREDIT.name());
        assertThat(saved.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getCredit()).isEqualTo(amount);
        assertThat(saved.getDescription()).isEqualTo(description);
    }

    @Test
    void recordAgentPayout_createsPayoutEntry() {
        UUID agentId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1000.00");
        String description = "Payout to agent";

        AgentLedger result = ledgerService.recordAgentPayout(agentId, payoutId, amount, description, UUID.randomUUID());

        verify(agentLedgerRepository).save(agentLedgerCaptor.capture());
        AgentLedger saved = agentLedgerCaptor.getValue();

        assertThat(saved.getAgentId()).isEqualTo(agentId);
        assertThat(saved.getPayoutId()).isEqualTo(payoutId);
        assertThat(saved.getTransactionType()).isEqualTo(LedgerTransactionType.DEBIT.name());
        assertThat(saved.getDebit()).isEqualTo(amount);
        assertThat(saved.getCredit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getDescription()).isEqualTo(description);
    }
}
