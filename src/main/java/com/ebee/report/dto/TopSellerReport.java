package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class TopSellerReport {
    private UUID productId;
    private String productName;
    private String itemCode;
    private String category;
    private Long quantitySold;
    private BigDecimal revenue;
    private int rank;
}
