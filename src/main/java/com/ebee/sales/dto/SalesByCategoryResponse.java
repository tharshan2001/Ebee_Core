package com.ebee.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SalesByCategoryResponse {

    private String category;
    private Long totalQuantity;
    private BigDecimal totalRevenue;
    private BigDecimal percentageOfTotal;
}
