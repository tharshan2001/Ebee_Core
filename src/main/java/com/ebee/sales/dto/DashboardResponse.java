package com.ebee.sales.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardResponse {
    private DashboardPeriod period;
    private DashboardSummary summary;
    private DashboardToday today;
    private DashboardPreviousPeriod previousPeriod;
}
