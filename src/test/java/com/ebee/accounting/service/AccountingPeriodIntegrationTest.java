package com.ebee.accounting.service;

import com.ebee.accounting.entity.AccountingPeriod;
import com.ebee.accounting.entity.AccountingPeriodTransaction;
import com.ebee.accounting.enums.PeriodStatus;
import com.ebee.accounting.repository.AccountingPeriodRepository;
import com.ebee.accounting.repository.AccountingPeriodTransactionRepository;
import com.ebee.accounting.repository.CustomerLedgerRepository;
import com.ebee.common.exception.PeriodClosedException;
import com.ebee.customer.entity.Customer;
import com.ebee.support.RealDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Accounting periods and the customer ledger against the real schema. Every test rolls back. */
@Transactional
class AccountingPeriodIntegrationTest extends RealDatabaseTest {

    @Autowired private AccountingPeriodService periods;
    @Autowired private LedgerService ledger;
    @Autowired private AccountingPeriodRepository periodRepository;
    @Autowired private AccountingPeriodTransactionRepository periodTransactions;
    @Autowired private CustomerLedgerRepository customerLedger;

    private UUID orderFor(Customer c) {
        return order(agent(), c, "1000.00").getId();
    }

    private AccountingPeriod currentPeriod() {
        LocalDate today = LocalDate.now();
        return periodRepository.findByMonthAndYear(today.getMonthValue(), today.getYear()).orElseThrow();
    }

    private List<AccountingPeriodTransaction> snapshots(UUID periodId) {
        return periodTransactions.findByPeriodIdOrderByRecordedAtDesc(periodId).stream()
                .filter(t -> "PERIOD_SNAPSHOT".equals(t.getTransactionType())).toList();
    }

    @Test
    void postingCreatesTheCurrentPeriod_andMirrorsTheEntryIntoIt() {
        Customer c = customer();
        UUID orderId = orderFor(c);

        ledger.recordCustomerInvoice(c.getId(), orderId, new BigDecimal("1500.00"), "Invoice", admin.getId());

        AccountingPeriod period = currentPeriod();
        assertThat(period.getStatus()).isEqualTo(PeriodStatus.OPEN);
        assertThat(periodTransactions.findByPeriodIdOrderByRecordedAtDesc(period.getId()))
                .anySatisfy(t -> {
                    assertThat(t.getTransactionType()).isEqualTo("INVOICE");
                    assertThat(t.getDebit()).isEqualByComparingTo("1500.00");
                });
    }

    @Test
    void invoicePaymentAndCreditNote_netToZero() {
        Customer c = customer();
        UUID orderId = orderFor(c);

        ledger.recordCustomerInvoice(c.getId(), orderId, new BigDecimal("1000.00"), "Invoice", admin.getId());
        ledger.recordCustomerPayment(c.getId(), null, new BigDecimal("400.00"), "Payment", admin.getId());
        assertThat(ledger.getCustomerBalance(c.getId())).isEqualByComparingTo("600.00");

        ledger.recordCustomerCreditNote(c.getId(), orderId, new BigDecimal("600.00"), "Return", admin.getId());
        assertThat(ledger.getCustomerBalance(c.getId())).isEqualByComparingTo("0");
    }

    @Test
    void negativeAmounts_areRejected_andNothingIsWritten() {
        Customer c = customer();
        assertThatThrownBy(() -> ledger.recordCustomerInvoice(c.getId(), orderFor(c),
                new BigDecimal("-1"), "bad", admin.getId())).isInstanceOf(IllegalArgumentException.class);
        assertThat(customerLedger.findByCustomerIdOrderByCreatedAtDesc(c.getId())).isEmpty();
    }

    @Test
    void closedPeriod_rejectsPostings_untilReopened() {
        Customer c = customer();
        ledger.recordCustomerInvoice(c.getId(), orderFor(c), new BigDecimal("100.00"), "Invoice", admin.getId());
        AccountingPeriod period = currentPeriod();

        periods.closePeriod(period.getId(), admin.getId(), "Month end");

        assertThatThrownBy(() -> ledger.recordCustomerPayment(c.getId(), null, new BigDecimal("50.00"), "Late payment", admin.getId()))
                .isInstanceOf(PeriodClosedException.class);

        periods.reopenPeriod(period.getId(), admin.getId(), "Correction");
        ledger.recordCustomerPayment(c.getId(), null, new BigDecimal("50.00"), "Late payment", admin.getId());
        assertThat(ledger.getCustomerBalance(c.getId())).isEqualByComparingTo("50.00");
    }

    @Test
    void closingSnapshot_totalsThePeriod_andReclosingDoesNotCountOldSnapshots() {
        Customer c = customer();
        AccountingPeriod period;
        ledger.recordCustomerInvoice(c.getId(), orderFor(c), new BigDecimal("700.00"), "Invoice", admin.getId());
        period = currentPeriod();
        List<AccountingPeriodTransaction> before = periodTransactions.findByPeriodIdOrderByRecordedAtDesc(period.getId())
                .stream().filter(t -> !"PERIOD_SNAPSHOT".equals(t.getTransactionType())).toList();
        BigDecimal debits = before.stream().map(AccountingPeriodTransaction::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = before.stream().map(AccountingPeriodTransaction::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);

        periods.closePeriod(period.getId(), admin.getId(), "Month end");
        periods.reopenPeriod(period.getId(), admin.getId(), "Correction");
        periods.closePeriod(period.getId(), admin.getId(), "Month end again");

        // The newest snapshot still equals the period's real postings, not real + previous snapshot.
        AccountingPeriodTransaction latest = snapshots(period.getId()).get(0);
        assertThat(latest.getDebit()).isEqualByComparingTo(debits);
        assertThat(latest.getCredit()).isEqualByComparingTo(credits);
    }

    @Test
    void onlyOpenPeriodsCanBeClosed_andHistoryIsAudited() {
        Customer c = customer();
        ledger.recordCustomerInvoice(c.getId(), orderFor(c), new BigDecimal("10.00"), "Invoice", admin.getId());
        AccountingPeriod period = currentPeriod();

        periods.closePeriod(period.getId(), admin.getId(), "Month end");
        assertThatThrownBy(() -> periods.closePeriod(period.getId(), admin.getId(), "again"))
                .isInstanceOf(IllegalStateException.class);
        periods.archivePeriod(period.getId(), admin.getId(), "Year end");

        assertThat(periodRepository.findById(period.getId()).orElseThrow().getStatus()).isEqualTo(PeriodStatus.ARCHIVED);
        assertThat(periods.getPeriodHistory(period.getId())).extracting(a -> a.getNewStatus())
                .contains(PeriodStatus.CLOSED, PeriodStatus.ARCHIVED);
    }
}
