package com.ebee.product.dto;

import java.math.BigDecimal;

public record LabelData(
        String productName,
        String itemCode,
        BigDecimal price,
        String category,
        String brand
) {}
