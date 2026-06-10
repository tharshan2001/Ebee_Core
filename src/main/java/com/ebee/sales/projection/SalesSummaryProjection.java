package com.ebee.sales.projection;

import java.math.BigDecimal;

public interface SalesSummaryProjection {
    BigDecimal getTotalRevenue();
    Long getTotalOrders();
}
