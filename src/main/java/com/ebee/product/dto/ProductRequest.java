package com.ebee.product.dto;

import com.ebee.common.enums.DiscountType;
import jakarta.validation.constraints.PositiveOrZero;
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

    @PositiveOrZero(message = "Price must be zero or positive")
    private BigDecimal price;

    @PositiveOrZero(message = "Cost price must be zero or positive")
    private BigDecimal costPrice;

    private List<MultipartFile> images;

    private List<String> removeImageUrls;

    private DiscountType discountType;

    private BigDecimal discountValue;

    /**
     * Optional cap on the per-order discount an admin can apply at order time.
     * When set, admin can supply a discount on each order item up to this cap.
     * When null, per-order discounts are not allowed (product uses its own
     * discountType/discountValue only).
     */
    private DiscountType maxDiscountType;

    private BigDecimal maxDiscountValue;

    /**
     * Set by the controller when the discount fields were supplied in the
     * request (even as empty strings). When true, updateProduct applies the
     * (possibly null) catalog discount fields; when false they are left
     * untouched on a partial update.
     */
    private boolean discountFieldsSet;

    /**
     * Same as {@link #discountFieldsSet} but for the maxDiscount pair.
     */
    private boolean maxDiscountFieldsSet;

    @Size(max = 100, message = "Brand must not exceed 100 characters")
    private String brand;

    /** VAT rate (tax_rates.id); omit to leave unchanged on update. */
    private java.util.UUID taxRateId;

    /** Batches must carry an expiry date and are sold earliest-expiry first. */
    private Boolean tracksExpiry;

    private String tags;

    private List<ProductSpecification> specifications;
}
