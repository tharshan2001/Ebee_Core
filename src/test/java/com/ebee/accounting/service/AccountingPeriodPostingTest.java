package com.ebee.accounting.service;

import com.ebee.accounting.entity.AccountingPeriod;
import com.ebee.accounting.entity.FiscalYear;
import com.ebee.accounting.enums.PeriodStatus;
import com.ebee.accounting.repository.AccountingPeriodAuditRepository;
import com.ebee.accounting.repository.AccountingPeriodRepository;
import com.ebee.accounting.repository.AccountingPeriodTransactionRepository;
import com.ebee.accounting.repository.FiscalYearRepository;
import com.ebee.common.exception.PeriodClosedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountingPeriodPostingTest {

    private final AccountingPeriodRepository periods = mock(AccountingPeriodRepository.class);
    private final AccountingPeriodTransactionRepository txns = mock(AccountingPeriodTransactionRepository.class);
    private final FiscalYearRepository fiscalYears = mock(FiscalYearRepository.class);
    private final AccountingPeriodService service = new AccountingPeriodService(periods,
            mock(AccountingPeriodAuditRepository.class), txns, fiscalYears);
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        when(periods.save(any())).thenAnswer(inv -> {
            AccountingPeriod p = inv.getArgument(0);
            if (p.getId() == null) {
                org.springframework.test.util.ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
            }
            return p;
        });
        when(fiscalYears.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void missingPeriod_isCreatedOnFirstPosting_includingItsFiscalYear() {
        when(periods.findByMonthAndYear(today.getMonthValue(), today.getYear())).thenReturn(Optional.empty());
        when(fiscalYears.findByFiscalYear(today.getYear()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(FiscalYear.builder().fiscalYear(today.getYear()).build()));

        service.recordPeriodTransaction("INVOICE", new BigDecimal("100"), BigDecimal.ZERO, UUID.randomUUID(), "ORDER", "x", null);

        verify(fiscalYears).save(argThat(fy -> fy.getFiscalYear() == today.getYear()));
        verify(periods).save(argThat(p -> p.getMonth() == today.getMonthValue() && p.getYear() == today.getYear()
                && p.getStatus() == PeriodStatus.OPEN));
        verify(txns).save(any());
    }

    @Test
    void closedPeriod_rejectsThePosting_insteadOfDroppingIt() {
        AccountingPeriod closed = AccountingPeriod.builder().month(today.getMonthValue()).year(today.getYear())
                .status(PeriodStatus.CLOSED).build();
        when(periods.findByMonthAndYear(today.getMonthValue(), today.getYear())).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.recordPeriodTransaction("PAYMENT", BigDecimal.ZERO, new BigDecimal("50"),
                UUID.randomUUID(), "PAYMENT", "x", null))
                .isInstanceOf(PeriodClosedException.class)
                .hasMessageContaining("CLOSED");
        verify(txns, never()).save(any());
    }

    @Test
    void monthlyJob_opensTheCurrentMonth_withYearAndMonthInTheRightOrder() {
        when(periods.findByMonthAndYear(today.getMonthValue(), today.getYear())).thenReturn(Optional.empty());
        when(fiscalYears.findByFiscalYear(today.getYear()))
                .thenReturn(Optional.of(FiscalYear.builder().fiscalYear(today.getYear()).build()));

        service.autoOpenCurrentMonthPeriod();

        // Before the fix it called openPeriod(month, year) and looked up "fiscal year 11".
        verify(fiscalYears, never()).findByFiscalYear(today.getMonthValue());
        verify(periods).save(argThat(p -> p.getYear() == today.getYear() && p.getMonth() == today.getMonthValue()));
    }
}
