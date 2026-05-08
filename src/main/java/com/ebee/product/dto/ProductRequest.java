package com.ebee.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

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

    private List<MultipartFile> images;
}
