package com.ebee.purchase.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderItemResponse(
        UUID id,
        UUID productId,
        String productName,
        String productItemCode,
        Integer quantity,
        Integer receivedQuantity,
        Integer returnedQuantity,
        BigDecimal unitPrice,
        BigDecimal sellingPrice,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal unitTax
) {}
