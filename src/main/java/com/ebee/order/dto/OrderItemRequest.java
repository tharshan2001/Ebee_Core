package com.ebee.order.dto;

import com.ebee.common.enums.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

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
    private DiscountType discountType;

    private BigDecimal discountValue;

    /**
     * Optional combo ID. When set, this order item represents a combo bundle.
     * The productItemCode is ignored; the combo's products are expanded for
     * stock movements and pricing is derived from the combo's rule.
     */
    private UUID comboId;

    /**
     * When true and the combo has a FREE_PRODUCT rule, instead of adding the
     * free product as a separate order item, deduct its price from the combo
     * total. The free product is not stocked (no inventory deduction).
     */
    private Boolean applyFreeProductAsDiscount;

    /** Sell by the pack: a product_units id for this product. quantity is then in packs. */
    private java.util.UUID unitId;
}
