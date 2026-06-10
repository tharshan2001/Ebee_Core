package com.ebee.report.service;

import com.ebee.agent.repository.AgentRepository;
import com.ebee.report.dto.AgentByDistrictReport;
import com.ebee.report.dto.AgentSummaryReport;
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
public class AgentReportService {

    private final AgentRepository agentRepository;

    public AgentSummaryReport getSummary(PeriodRange range) {
        LocalDateTime start = toStartDateTime(range.startDate());
        LocalDateTime end = toEndDateTime(range.endDate());

        long active = agentRepository.findAllWithUser().stream().filter(a -> Boolean.TRUE.equals(a.getIsActive())).count();
        long inactive = agentRepository.findAllWithUser().stream().filter(a -> !Boolean.TRUE.equals(a.getIsActive())).count();
        long total = agentRepository.count();
        long newAgents = agentRepository.countNewAgentsSince(start, end);

        return AgentSummaryReport.builder()
                .totalActive(active)
                .totalInactive(inactive)
                .newRegistrations(newAgents)
                .totalAgents(total)
                .period(range)
                .build();
    }

    public List<AgentByDistrictReport> getByDistrict() {
        List<Object[]> results = agentRepository.findAgentDistrictDistribution();
        List<AgentByDistrictReport> list = new ArrayList<>();
        for (Object[] row : results) {
            String district = row[0] instanceof Enum<?> e ? e.name() : (row[0] != null ? row[0].toString() : "Unknown");
            list.add(AgentByDistrictReport.builder()
                    .district(district)
                    .agentCount(toLong(row[1]))
                    .activeCount(toLong(row[2]))
                    .build());
        }
        return list;
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
