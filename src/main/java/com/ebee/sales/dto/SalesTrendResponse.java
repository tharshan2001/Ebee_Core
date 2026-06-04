package com.ebee.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SalesTrendResponse {

    private LocalDateTime period;
    private Long totalOrders;
    private BigDecimal totalRevenue;
}
