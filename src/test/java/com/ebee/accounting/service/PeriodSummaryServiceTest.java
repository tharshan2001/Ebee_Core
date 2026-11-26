package com.ebee.accounting.service;

import com.ebee.accounting.dto.PeriodSummaryResponse;
import com.ebee.accounting.entity.AccountingPeriod;
import com.ebee.accounting.entity.AccountingPeriodAudit;
import com.ebee.accounting.entity.AccountingPeriodTransaction;
import com.ebee.accounting.enums.PeriodStatus;
import com.ebee.accounting.repository.AccountingPeriodAuditRepository;
import com.ebee.accounting.repository.AccountingPeriodRepository;
import com.ebee.accounting.repository.AccountingPeriodTransactionRepository;
import com.ebee.user.entity.User;
import com.ebee.user.service.UserLookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeriodSummaryServiceTest {

    @Mock private AccountingPeriodRepository periodRepository;
    @Mock private AccountingPeriodTransactionRepository transactionRepository;
    @Mock private AccountingPeriodAuditRepository auditRepository;
    @Mock private UserLookupService userLookupService;
    @InjectMocks private PeriodSummaryService service;

    private final UUID periodId = UUID.randomUUID();

    private AccountingPeriodTransaction tx(String ref, String type, String debit, String credit) {
        return AccountingPeriodTransaction.builder().periodId(periodId).referenceType(ref).transactionType(type)
                .debit(new BigDecimal(debit)).credit(new BigDecimal(credit)).build();
    }

    @Test
    void groupsPostingsByKind_leavesOutSnapshots_andNamesWhoChangedStatus() {
        UUID admin = UUID.randomUUID();
        when(periodRepository.findById(periodId)).thenReturn(Optional.of(AccountingPeriod.builder()
                .id(periodId).month(11).year(2026).status(PeriodStatus.CLOSED).build()));
        when(transactionRepository.findByPeriodIdOrderByRecordedAtDesc(periodId)).thenReturn(List.of(
                tx("ORDER", "INVOICE", "1000", "0"),
                tx("ORDER", "INVOICE", "500", "0"),
                tx("PAYMENT", "PAYMENT", "0", "700"),
                tx(null, AccountingPeriodService.SNAPSHOT_TYPE, "1500", "700")));
        when(auditRepository.findByPeriodIdOrderByChangedAtDesc(periodId)).thenReturn(List.of(
                AccountingPeriodAudit.builder().periodId(periodId).previousStatus(PeriodStatus.OPEN)
                        .newStatus(PeriodStatus.CLOSED).changedBy(admin).changedAt(LocalDateTime.now()).reason("Month end").build()));
        when(userLookupService.findById(admin)).thenReturn(Optional.of(User.builder().firstName("Ada").lastName("Admin").build()));

        PeriodSummaryResponse r = service.summarize(periodId);

        assertThat(r.postingCount()).isEqualTo(3);
        assertThat(r.lines()).hasSize(2);
        PeriodSummaryResponse.Line invoices = r.lines().get(0);
        assertThat(invoices.referenceType()).isEqualTo("ORDER");
        assertThat(invoices.count()).isEqualTo(2);
        assertThat(invoices.debit()).isEqualByComparingTo("1500");
        assertThat(r.lines().get(1).credit()).isEqualByComparingTo("700");
        assertThat(r.history()).singleElement().satisfies(h -> {
            assertThat(h.changedByName()).isEqualTo("Ada Admin");
            assertThat(h.reason()).isEqualTo("Month end");
        });
    }
}
