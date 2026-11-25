package com.ebee.product.dto;

import com.ebee.inventory.dto.InventoryResponse;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class ProductResponse {

    private UUID id;
    private String name;
    private String itemCode;
    private String description;
    private String category;
    private BigDecimal price;
    private BigDecimal costPrice;
    private Boolean isActive;
    private String discountType;
    private BigDecimal discountValue;
    private String maxDiscountType;
    private BigDecimal maxDiscountValue;
    private String brand;
    private java.util.UUID taxRateId;
    private Boolean tracksExpiry;
    private String tags;
    private List<ProductSpecification> specifications;
    private Boolean featured;
    private Long totalSold;
    private BigDecimal effectivePrice;
    private List<String> imageUrls;
    private List<ProductImageResponse> images;
    private InventoryResponse inventory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
