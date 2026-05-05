package com.ebee.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    @NotBlank(message = "SKU is required")
    private String sku;

    private String description;

    @NotBlank(message = "Category is required")
    private String category;

    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @Positive(message = "Cost price must be positive")
    private BigDecimal costPrice;

    @PositiveOrZero(message = "Quantity in stock must be zero or positive")
    private Integer quantityInStock;

    private Integer reorderLevel;
}
