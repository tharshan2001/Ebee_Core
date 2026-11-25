package com.ebee.product.entity;

import com.ebee.inventory.entity.Inventory;
import com.ebee.order.entity.OrderItem;
import com.ebee.product.dto.ProductSpecification;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@BatchSize(size = 30)
@Table(name = "products", uniqueConstraints = {
    @UniqueConstraint(columnNames = "item_code")
})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
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

    /** Batches must carry an expiry date; sold earliest-expiry first; expired batches are never sold. */
    @Column(name = "tracks_expiry", nullable = false)
    private Boolean tracksExpiry;

    public boolean tracksExpiry() {
        return Boolean.TRUE.equals(tracksExpiry);
    }

    @PrePersist
    protected void applyExpiryDefault() {
        if (tracksExpiry == null) {
            tracksExpiry = Boolean.FALSE;
        }
    }

    /** VAT rate for this product (tax_rates.id); null = exempt. */
    @Column(name = "tax_rate_id")
    private UUID taxRateId;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private List<ProductSpecification> specifications = new ArrayList<>();

    @Column(name = "featured")
    @Builder.Default
    private Boolean featured = false;

    /** One stock row per location. */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.Set<Inventory> inventories;

    public java.util.Set<Inventory> getInventories() {
        if (inventories == null) {
            inventories = new java.util.HashSet<>();
        }
        return inventories;
    }

    @OneToMany(mappedBy = "product")
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
        return getEffectivePrice(price, 1);
    }

    /**
     * Catalog discount applied to {@code listPrice} — the price of one sales unit (a pack of
     * {@code unitsPerSalesUnit} stock units). FIXED catalog discounts are per stock unit, so they
     * scale with the pack size.
     */
    public BigDecimal getEffectivePrice(BigDecimal listPrice, int unitsPerSalesUnit) {
        if (discountType == null || discountValue == null) {
            return listPrice;
        }
        return switch (discountType) {
            case "PERCENTAGE" -> listPrice.multiply(BigDecimal.ONE.subtract(
                            discountValue.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP)
                    .max(BigDecimal.ZERO);
            case "FIXED" -> listPrice.subtract(discountValue.multiply(BigDecimal.valueOf(unitsPerSalesUnit))).max(BigDecimal.ZERO);
            default -> listPrice;
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
        return getPriceWithDiscount(price, 1, customType, customValue);
    }

    /**
     * Per-order discount on one sales unit priced {@code listPrice} (a pack of
     * {@code unitsPerSalesUnit} stock units, or a customer group price). A FIXED discount is for
     * the whole sales unit, so the product's FIXED maximum (set per stock unit) scales with the pack.
     */
    public BigDecimal getPriceWithDiscount(BigDecimal listPrice, int unitsPerSalesUnit, String customType,
                                           BigDecimal customValue) {
        if (customType == null || customValue == null) {
            return getEffectivePrice(listPrice, unitsPerSalesUnit);
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
        // Reject mixed catalog + per-order discount types. If the product has its
        // own catalog discount (e.g. FIXED 300) the per-order discount must use
        // the same type; mixing types is ambiguous (does the per-order apply to
        // the original price or the post-catalog price?) and the frontend's
        // "Type mismatch" warning has been showing without any backend guard.
        if (discountType != null && !discountType.equals(customType)) {
            throw new IllegalArgumentException(
                    "Per-order discount type '" + customType + "' does not match product's catalog discount type '"
                            + discountType + "' for product '" + (name != null ? name : itemCode) + "'");
        }
        if (customValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("discountValue must not be negative");
        }
        BigDecimal maxAllowed = "FIXED".equals(customType)
                ? maxDiscountValue.multiply(BigDecimal.valueOf(unitsPerSalesUnit)) : maxDiscountValue;
        if (customValue.compareTo(maxAllowed) > 0) {
            throw new IllegalArgumentException(
                    "Discount " + customValue + " " + customType
                            + " exceeds the maximum allowed (" + maxAllowed + " " + maxDiscountType
                            + ") for product '" + (name != null ? name : itemCode) + "'");
        }
        // Apply per-order discount on top of the product's own (catalog) discount
        BigDecimal basePrice = getEffectivePrice(listPrice, unitsPerSalesUnit);
        return switch (customType) {
            case "PERCENTAGE" -> {
                // Guard against 100%+ discount: that would make the product free
                // and effectively subsidize the buyer beyond the cap. The cap check
                // above only catches this when maxDiscountValue <= 100.
                if (customValue.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new IllegalArgumentException(
                            "PERCENTAGE discount " + customValue + " exceeds 100% for product '"
                                    + (name != null ? name : itemCode) + "'");
                }
                yield basePrice.multiply(BigDecimal.ONE.subtract(
                                customValue.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)))
                        .setScale(2, RoundingMode.HALF_UP)
                        .max(BigDecimal.ZERO);
            }
            case "FIXED" -> {
                // A FIXED discount that exceeds the base price would floor at zero
                // and let admin order the product for free. Reject instead.
                if (customValue.compareTo(basePrice) > 0) {
                    throw new IllegalArgumentException(
                            "FIXED discount " + customValue + " exceeds the (effective) price "
                                    + basePrice + " for product '" + (name != null ? name : itemCode) + "'");
                }
                yield basePrice.subtract(customValue).setScale(2, RoundingMode.HALF_UP);
            }
            default -> basePrice;
        };
    }
}
