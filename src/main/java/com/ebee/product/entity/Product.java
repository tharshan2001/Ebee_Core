package com.ebee.product.entity;

import com.ebee.inventory.entity.Inventory;
import com.ebee.order.entity.OrderItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products", uniqueConstraints = {
    @UniqueConstraint(columnNames = "item_code")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "item_code", nullable = false, unique = true)
    private String itemCode;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "cost_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "discount_type", length = 10)
    private String discountType;

    @Column(name = "discount_value", precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_discount_type", length = 10)
    private String maxDiscountType;

    @Column(name = "max_discount_value", precision = 10, scale = 2)
    private BigDecimal maxDiscountValue;

    @Column(length = 100)
    private String brand;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private Inventory inventory;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "product", orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public BigDecimal getEffectivePrice() {
        if (discountType == null || discountValue == null) {
            return price;
        }
        return switch (discountType) {
            case "PERCENTAGE" -> price.multiply(BigDecimal.ONE.subtract(
                            discountValue.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP)
                    .max(BigDecimal.ZERO);
            case "FIXED" -> price.subtract(discountValue).max(BigDecimal.ZERO);
            default -> price;
        };
    }

    /**
     * Computes the effective price using a custom discount supplied at order time
     * (e.g. admin-applied per-order discount). The custom discount is capped at
     * the product's {@code maxDiscount} (set by admin at product creation/update).
     * If {@code maxDiscount} is null, the custom discount is rejected (admin did
     * not authorize any per-order discount for this product).
     *
     * <p>The product's own {@code discountType}/{@code discountValue} is applied
     * <em>first</em> (via {@link #getEffectivePrice()}), then the per-order
     * discount is applied on top of that. This lets a product carry a permanent
     * catalog discount and still allow admins to layer an order-time discount
     * on top, both within the admin-set cap.
     *
     * @param customType   "PERCENTAGE" or "FIXED" (nullable for no discount)
     * @param customValue  the discount value (nullable for no discount)
     * @return price after applying the product's own discount and the custom discount
     * @throws IllegalArgumentException if custom discount exceeds the cap
     */
    public BigDecimal getPriceWithDiscount(String customType, BigDecimal customValue) {
        if (customType == null || customValue == null) {
            return getEffectivePrice();
        }
        if (maxDiscountType == null || maxDiscountValue == null) {
            throw new IllegalArgumentException(
                    "Product '" + (name != null ? name : itemCode)
                            + "' does not allow per-order discount (maxDiscount not set)");
        }
        if (!"PERCENTAGE".equals(customType) && !"FIXED".equals(customType)) {
            throw new IllegalArgumentException("discountType must be PERCENTAGE or FIXED");
        }
        if (!maxDiscountType.equals(customType)) {
            throw new IllegalArgumentException(
                    "discountType '" + customType + "' does not match product maxDiscountType '"
                            + maxDiscountType + "'");
        }
        if (customValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("discountValue must not be negative");
        }
        if (customValue.compareTo(maxDiscountValue) > 0) {
            throw new IllegalArgumentException(
                    "Discount " + customValue + " " + customType
                            + " exceeds the maximum allowed (" + maxDiscountValue + " " + maxDiscountType
                            + ") for product '" + (name != null ? name : itemCode) + "'");
        }
        // Apply per-order discount on top of the product's own (catalog) discount
        BigDecimal basePrice = getEffectivePrice();
        return switch (customType) {
            case "PERCENTAGE" -> basePrice.multiply(BigDecimal.ONE.subtract(
                            customValue.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP)
                    .max(BigDecimal.ZERO);
            case "FIXED" -> basePrice.subtract(customValue).max(BigDecimal.ZERO);
            default -> basePrice;
        };
    }
}
