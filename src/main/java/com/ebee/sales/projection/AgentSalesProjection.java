package com.ebee.sales.projection;

import java.math.BigDecimal;
import java.util.UUID;

public interface AgentSalesProjection {
    UUID getAgentId();
    String getAgentCode();
    String getFirstName();
    String getLastName();
    Long getTotalOrders();
    BigDecimal getTotalRevenue();
}
