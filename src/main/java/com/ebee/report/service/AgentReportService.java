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
import com.ebee.common.util.DateUtils;
import com.ebee.common.util.NumberUtils;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentReportService {

    private final AgentRepository agentRepository;

    public AgentSummaryReport getSummary(PeriodRange range) {
        LocalDateTime start = DateUtils.toStartDateTime(range.startDate());
        LocalDateTime end = DateUtils.toEndDateTime(range.endDate());

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
                    .agentCount(NumberUtils.toLong(row[1]))
                    .activeCount(NumberUtils.toLong(row[2]))
                    .build());
        }
        return list;
    }

}
