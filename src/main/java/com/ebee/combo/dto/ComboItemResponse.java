package com.ebee.combo.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ComboItemResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private String productItemCode;
    private BigDecimal productPrice;
    private Integer quantity;
    private BigDecimal lineTotal;
}
