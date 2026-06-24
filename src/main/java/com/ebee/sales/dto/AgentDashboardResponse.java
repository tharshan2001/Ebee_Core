package com.ebee.sales.dto;

import java.math.BigDecimal;

public record AgentDashboardResponse(
    long ordersToday,
    BigDecimal totalCommission,
    BigDecimal thisWeekCommission
) {}
