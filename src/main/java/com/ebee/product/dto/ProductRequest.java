package com.ebee.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductRequest {

    private String name;

    private String itemCode;

    private String description;

    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;

    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @Positive(message = "Cost price must be positive")
    private BigDecimal costPrice;

    private List<MultipartFile> images;

    private List<String> removeImageUrls;

    private String discountType;

    private BigDecimal discountValue;

    /**
     * Optional cap on the per-order discount an admin can apply at order time.
     * When set, admin can supply a discount on each order item up to this cap.
     * When null, per-order discounts are not allowed (product uses its own
     * discountType/discountValue only).
     */
    private String maxDiscountType;

    private BigDecimal maxDiscountValue;

    @Size(max = 100, message = "Brand must not exceed 100 characters")
    private String brand;

    private String tags;
}
