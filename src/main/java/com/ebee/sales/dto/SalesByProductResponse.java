package com.ebee.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class SalesByProductResponse {

    private UUID productId;
    private String productName;
    private String itemCode;
    private String category;
    private Long totalQuantity;
    private BigDecimal totalRevenue;
}
