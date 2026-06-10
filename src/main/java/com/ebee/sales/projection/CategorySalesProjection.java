package com.ebee.sales.projection;

import java.math.BigDecimal;

public interface CategorySalesProjection {
    String getCategory();
    Long getTotalQuantity();
    BigDecimal getTotalRevenue();
}
