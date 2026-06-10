package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentSummaryReport {
    private Long totalActive;
    private Long totalInactive;
    private Long newRegistrations;
    private Long totalAgents;
    private PeriodRange period;
}
