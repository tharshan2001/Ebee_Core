package com.ebee.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ReturnRequestItemResponse(
    UUID id,
    UUID orderItemId,
    String productName,
    String productItemCode,
    Integer quantity,
    BigDecimal unitRefundAmount,
    BigDecimal subtotal
) {}
