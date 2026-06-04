package com.ebee.sales.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardToday {
    private Long ordersToday;
    private Long ordersYesterday;
    private Long deliveriesToday;
    private Long deliveriesYesterday;
}
