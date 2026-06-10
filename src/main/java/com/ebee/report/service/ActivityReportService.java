package com.ebee.report.service;

import com.ebee.audit.repository.AuditLogRepository;
import com.ebee.report.dto.ActivitySummaryReport;
import com.ebee.report.dto.PeriodRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityReportService {

    private final AuditLogRepository auditLogRepository;

    public ActivitySummaryReport getAuditSummary(PeriodRange range) {
        LocalDateTime start = toStartDateTime(range.startDate());
        LocalDateTime end = toEndDateTime(range.endDate());

        List<Object[]> results = auditLogRepository.findActionSummaryByDateRange(start, end);
        long total = 0;
        List<ActivitySummaryReport.ActionBreakdown> breakdowns = new ArrayList<>();

        for (Object[] row : results) {
            String action = row[0] instanceof Enum<?> e ? e.name() : (row[0] != null ? row[0].toString() : "Unknown");
            long count = toLong(row[1]);
            long uniqueUsers = toLong(row[2]);
            total += count;
            breakdowns.add(ActivitySummaryReport.ActionBreakdown.builder()
                    .action(action)
                    .count(count)
                    .uniqueUsers(uniqueUsers)
                    .build());
        }

        return ActivitySummaryReport.builder()
                .totalActions(total)
                .actionBreakdown(breakdowns)
                .period(range)
                .build();
    }

    private LocalDateTime toStartDateTime(LocalDate date) {
        return date != null ? date.atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
    }

    private LocalDateTime toEndDateTime(LocalDate date) {
        return date != null ? date.plusDays(1).atStartOfDay() : LocalDateTime.of(9999, 12, 31, 23, 59);
    }

    private Long toLong(Object obj) {
        if (obj instanceof Number n) return n.longValue();
        return 0L;
    }
}
