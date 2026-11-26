package com.ebee.accounting.service;

import com.ebee.accounting.dto.AccountingPeriodResponse;
import com.ebee.accounting.dto.PeriodSummaryResponse;
import com.ebee.accounting.entity.AccountingPeriod;
import com.ebee.accounting.entity.AccountingPeriodAudit;
import com.ebee.accounting.entity.AccountingPeriodTransaction;
import com.ebee.accounting.repository.AccountingPeriodAuditRepository;
import com.ebee.accounting.repository.AccountingPeriodRepository;
import com.ebee.accounting.repository.AccountingPeriodTransactionRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.user.entity.User;
import com.ebee.user.service.UserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** What was posted in one accounting period, by kind, and how its status changed. Read only. */
@Service
@RequiredArgsConstructor
public class PeriodSummaryService {

    private final AccountingPeriodRepository periodRepository;
    private final AccountingPeriodTransactionRepository transactionRepository;
    private final AccountingPeriodAuditRepository auditRepository;
    private final UserLookupService userLookupService;

    @Transactional(readOnly = true)
    public PeriodSummaryResponse summarize(UUID periodId) {
        AccountingPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("AccountingPeriod", "id", periodId));

        // Group real postings by (reference type, transaction type); snapshots are period-end totals, not postings.
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, BigDecimal[]> sums = new LinkedHashMap<>();
        Map<String, String[]> keys = new LinkedHashMap<>();
        long postings = 0;
        for (AccountingPeriodTransaction t : transactionRepository.findByPeriodIdOrderByRecordedAtDesc(periodId)) {
            if (AccountingPeriodService.SNAPSHOT_TYPE.equals(t.getTransactionType())) continue;
            postings++;
            String key = t.getReferenceType() + "|" + t.getTransactionType();
            keys.putIfAbsent(key, new String[] {t.getReferenceType(), t.getTransactionType()});
            counts.computeIfAbsent(key, k -> new long[1])[0]++;
            BigDecimal[] s = sums.computeIfAbsent(key, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            s[0] = s[0].add(Objects.requireNonNullElse(t.getDebit(), BigDecimal.ZERO));
            s[1] = s[1].add(Objects.requireNonNullElse(t.getCredit(), BigDecimal.ZERO));
        }
        List<PeriodSummaryResponse.Line> lines = keys.entrySet().stream()
                .map(e -> new PeriodSummaryResponse.Line(e.getValue()[0], e.getValue()[1],
                        counts.get(e.getKey())[0], sums.get(e.getKey())[0], sums.get(e.getKey())[1]))
                .sorted(Comparator.comparingLong(PeriodSummaryResponse.Line::count).reversed())
                .toList();

        Map<UUID, String> names = new HashMap<>();
        List<PeriodSummaryResponse.StatusChange> history = auditRepository.findByPeriodIdOrderByChangedAtDesc(periodId)
                .stream()
                .map((AccountingPeriodAudit a) -> new PeriodSummaryResponse.StatusChange(
                        a.getPreviousStatus(), a.getNewStatus(), a.getChangedAt(),
                        a.getChangedBy() == null ? null : names.computeIfAbsent(a.getChangedBy(), this::nameOf),
                        a.getReason()))
                .toList();

        AccountingPeriodResponse p = new AccountingPeriodResponse(period.getId(), period.getMonth(), period.getYear(),
                period.getStatus(), period.getOpenedAt(), period.getClosedAt(), null);
        return new PeriodSummaryResponse(p, postings, lines, history);
    }

    private String nameOf(UUID userId) {
        return userLookupService.findById(userId)
                .map((User u) -> ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                        + (u.getLastName() == null ? "" : u.getLastName())).trim())
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }
}
