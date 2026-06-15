package com.ebee.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemRequest {

    @NotBlank(message = "Product item code is required")
    private String productItemCode;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    /**
     * Optional per-item discount applied at order time (admin only). Must be
     * within the product's maxDiscount cap (set at product creation/update).
     * Both fields must be supplied together, or both omitted for no discount.
     */
    private String discountType;

    private BigDecimal discountValue;
}
