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
    /** e.g. "Box of 12"; null when sold by the stock unit. quantity is in this unit. */
    private String salesUnitName;
    private Integer unitsPerSalesUnit;
    private BigDecimal unitPrice;
    private BigDecimal originalUnitPrice;
    private BigDecimal subtotal;
    private BigDecimal taxRate;
    private BigDecimal unitTax;
    private BigDecimal taxAmount;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal costPrice;
    private UUID comboId;
    private String comboName;
    private Boolean isFreeProduct;
    private Integer returnedQuantity;
    private Integer returnableQuantity;
}
