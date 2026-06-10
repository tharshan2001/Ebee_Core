package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentByDistrictReport {
    private String district;
    private Long agentCount;
    private Long activeCount;
}
