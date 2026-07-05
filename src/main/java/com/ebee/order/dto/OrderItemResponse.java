package com.ebee.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class OrderItemResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private String productItemCode;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal originalUnitPrice;
    private BigDecimal subtotal;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal costPrice;
}
