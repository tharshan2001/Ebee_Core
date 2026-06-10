package com.ebee.sales.projection;

import java.math.BigDecimal;
import java.util.UUID;

public interface ProductSalesProjection {
    UUID getProductId();
    String getProductName();
    String getItemCode();
    String getCategory();
    Long getTotalQuantity();
    BigDecimal getTotalRevenue();
}
