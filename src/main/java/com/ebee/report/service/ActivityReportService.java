package com.ebee.report.service;

import com.ebee.audit.repository.AuditLogRepository;
import com.ebee.report.dto.ActivitySummaryReport;
import com.ebee.report.dto.PeriodRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.ebee.common.util.DateUtils;
import com.ebee.common.util.NumberUtils;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityReportService {

    private final AuditLogRepository auditLogRepository;

    public ActivitySummaryReport getAuditSummary(PeriodRange range) {
        LocalDateTime start = DateUtils.toStartDateTime(range.startDate());
        LocalDateTime end = DateUtils.toEndDateTime(range.endDate());

        List<Object[]> results = auditLogRepository.findActionSummaryByDateRange(start, end);
        long total = 0;
        List<ActivitySummaryReport.ActionBreakdown> breakdowns = new ArrayList<>();

        for (Object[] row : results) {
            String action = row[0] instanceof Enum<?> e ? e.name() : (row[0] != null ? row[0].toString() : "Unknown");
            long count = NumberUtils.toLong(row[1]);
            long uniqueUsers = NumberUtils.toLong(row[2]);
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

}
