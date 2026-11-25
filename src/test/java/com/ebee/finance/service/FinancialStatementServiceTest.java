package com.ebee.finance.service;

import com.ebee.accounting.repository.AgentLedgerRepository;
import com.ebee.accounting.repository.CustomerLedgerRepository;
import com.ebee.accounting.repository.SupplierLedgerRepository;
import com.ebee.customer.repository.CustomerAdvanceTransactionRepository;
import com.ebee.finance.entity.AccountTransaction.Direction;
import com.ebee.finance.entity.AccountTransaction.Type;
import com.ebee.finance.entity.PaymentAccount;
import com.ebee.finance.repository.AccountTransactionRepository;
import com.ebee.finance.repository.PaymentAccountRepository;
import com.ebee.inventory.repository.InventoryLayerRepository;
import com.ebee.tax.entity.VatEntry;
import com.ebee.tax.repository.VatEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancialStatementServiceTest {

    private final PaymentAccountRepository accountRepo = mock(PaymentAccountRepository.class);
    private final AccountTransactionRepository txnRepo = mock(AccountTransactionRepository.class);
    private final MoneyAccountService money = mock(MoneyAccountService.class);
    private final CustomerLedgerRepository customerLedger = mock(CustomerLedgerRepository.class);
    private final SupplierLedgerRepository supplierLedger = mock(SupplierLedgerRepository.class);
    private final AgentLedgerRepository agentLedger = mock(AgentLedgerRepository.class);
    private final CustomerAdvanceTransactionRepository advances = mock(CustomerAdvanceTransactionRepository.class);
    private final VatEntryRepository vat = mock(VatEntryRepository.class);
    private final InventoryLayerRepository layers = mock(InventoryLayerRepository.class);
    private FinancialStatementService service;
    private PaymentAccount cash;
    private PaymentAccount bank;

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static Object[] row(Object a, Object b) {
        return new Object[]{a, b};
    }

    @BeforeEach
    void setUp() {
        service = new FinancialStatementService(accountRepo, txnRepo, money, customerLedger, supplierLedger,
                agentLedger, advances, vat, layers);
        cash = new PaymentAccount("Cash", PaymentAccount.Type.CASH, null, null);
        bank = new PaymentAccount("Bank", PaymentAccount.Type.BANK, null, null);
        ReflectionTestUtils.setField(cash, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(bank, "id", UUID.randomUUID());
        when(accountRepo.findAllByOrderByNameAsc()).thenReturn(List.of(bank, cash));
    }

    @Test
    void balanceSheet_balancesAndSplitsEachPartyBySign() {
        LocalDate asOf = LocalDate.of(2026, 11, 30);
        when(money.balancesAsOf(asOf)).thenReturn(Map.of(cash.getId(), bd("15000"), bank.getId(), bd("85000")));
        // Customers: A owes 20,000; B has 1,500 credit with us.
        when(customerLedger.balancesBefore(any())).thenReturn(List.of(row(UUID.randomUUID(), bd("20000")), row(UUID.randomUUID(), bd("-1500"))));
        // Suppliers: we owe 30,000 to one; prepaid 2,000 to another.
        when(supplierLedger.balancesBefore(any())).thenReturn(List.of(row(UUID.randomUUID(), bd("30000")), row(UUID.randomUUID(), bd("-2000"))));
        // Agents: owe 4,000 commission to one; another owes us a 600 clawback.
        when(agentLedger.balancesBefore(any())).thenReturn(List.of(row(UUID.randomUUID(), bd("4000")), row(UUID.randomUUID(), bd("-600"))));
        when(advances.sumAllBefore(any())).thenReturn(bd("3000"));
        when(vat.sumTaxAsOf(eq(VatEntry.Direction.OUTPUT), eq(asOf))).thenReturn(bd("9000"));
        when(vat.sumTaxAsOf(eq(VatEntry.Direction.INPUT), eq(asOf))).thenReturn(bd("5400"));
        when(layers.currentStockValue()).thenReturn(bd("50000.4"));

        var bs = service.balanceSheet(asOf);

        assertThat(bs.totalCashAndBank()).isEqualByComparingTo("100000");
        assertThat(bs.customerReceivables()).isEqualByComparingTo("20000");
        assertThat(bs.customerCredits()).isEqualByComparingTo("1500");
        assertThat(bs.supplierPayables()).isEqualByComparingTo("30000");
        assertThat(bs.supplierAdvances()).isEqualByComparingTo("2000");
        assertThat(bs.commissionPayable()).isEqualByComparingTo("4000");
        assertThat(bs.agentClawbacksReceivable()).isEqualByComparingTo("600");
        assertThat(bs.vatPayable()).isEqualByComparingTo("3600");
        assertThat(bs.stockAtCost()).isEqualByComparingTo("50000.40");
        // Assets 100,000 + 20,000 + 50,000.40 + 600 + 2,000 = 172,600.40
        assertThat(bs.totalAssets()).isEqualByComparingTo("172600.40");
        // Liabilities 30,000 + 4,000 + 3,000 + 1,500 + 3,600 = 42,100
        assertThat(bs.totalLiabilities()).isEqualByComparingTo("42100");
        assertThat(bs.equity()).isEqualByComparingTo(bs.totalAssets().subtract(bs.totalLiabilities()));
    }

    @Test
    void cashFlow_groupsByActivity_andReconcilesOpeningToClosing() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 30);
        when(txnRepo.flowsByType(from, to)).thenReturn(List.of(
                new Object[]{Type.CUSTOMER_RECEIPT, Direction.IN, bd("50000")},
                new Object[]{Type.CUSTOMER_REFUND, Direction.OUT, bd("2000")},
                new Object[]{Type.SUPPLIER_PAYMENT, Direction.OUT, bd("30000")},
                new Object[]{Type.EXPENSE, Direction.OUT, bd("5000")},
                new Object[]{Type.OWNER_WITHDRAWAL, Direction.OUT, bd("3000")}));
        when(money.balancesAsOf(from.minusDays(1))).thenReturn(Map.of(cash.getId(), bd("10000")));
        when(money.balancesAsOf(to)).thenReturn(Map.of(cash.getId(), bd("20000")));

        var cf = service.cashFlow(from, to);

        assertThat(cf.netOperating()).isEqualByComparingTo("13000");
        assertThat(cf.netFinancing()).isEqualByComparingTo("-3000");
        assertThat(cf.netChange()).isEqualByComparingTo("10000");
        assertThat(cf.openingCash().add(cf.netChange())).isEqualByComparingTo(cf.closingCash());
    }
}
