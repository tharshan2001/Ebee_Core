package com.ebee.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ProductResponse {

    private UUID id;
    private String name;
    private String sku;
    private String description;
    private String category;
    private BigDecimal price;
    private BigDecimal costPrice;
    private Integer quantityInStock;
    private Integer reorderLevel;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
