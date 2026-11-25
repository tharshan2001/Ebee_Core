package com.ebee.order.entity;

import com.ebee.combo.entity.Combo;
import com.ebee.product.entity.Product;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@BatchSize(size = 30)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "original_unit_price", precision = 10, scale = 2)
    private BigDecimal originalUnitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_type", length = 10)
    private String discountType;

    @Column(name = "discount_value", precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "cost_price", precision = 14, scale = 4)
    private BigDecimal costPrice;

    /** Selling unit (product_units.id) when sold by the pack; null = by the stock unit. */
    @Column(name = "sales_unit_id")
    private java.util.UUID salesUnitId;

    /** e.g. "Box of 12" (kept even if the pack is changed later). */
    @Column(name = "sales_unit_name", length = 50)
    private String salesUnitName;

    /**
     * Stock units per unit of this line. quantity, returnedQuantity, unitPrice, unitTax and
     * costPrice are all per sales unit; stock moves quantity × this.
     */
    @Column(name = "units_per_sales_unit", nullable = false)
    private Integer unitsPerSalesUnit;

    /** VAT rate applied (percent); null = no VAT, or a combo with mixed rates. */
    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    /** VAT inside unitPrice, per unit. unitPrice stays the gross price charged. */
    @Column(name = "unit_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitTax;

    /** unitTax × quantity at the time of sale. */
    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id")
    private Combo combo;

    @Column(name = "returned_quantity", nullable = false)
    @Builder.Default
    private Integer returnedQuantity = 0;

    @Column(name = "is_free_product", nullable = false)
    @Builder.Default
    private Boolean isFreeProduct = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Transient
    public int getReturnableQuantity() {
        return quantity - returnedQuantity;
    }

    @Transient
    public boolean isFullyReturned() {
        return returnedQuantity >= quantity;
    }

    public BigDecimal getUnitTax() {
        return unitTax == null ? BigDecimal.ZERO : unitTax;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount == null ? BigDecimal.ZERO : taxAmount;
    }

    /** VAT on the units still kept by the customer. */
    public BigDecimal getRetainedTax() {
        return getUnitTax().multiply(BigDecimal.valueOf(getReturnableQuantity()));
    }

    @jakarta.persistence.PrePersist
    protected void applyTaxDefaults() {
        // JPA allows one @PrePersist per entity: tax and unit defaults both live here.
        if (unitsPerSalesUnit == null) {
            unitsPerSalesUnit = 1;
        }
        if (unitTax == null) {
            unitTax = BigDecimal.ZERO;
        }
        if (taxAmount == null) {
            taxAmount = BigDecimal.ZERO;
        }
    }

    public int getUnitsPerSalesUnit() {
        return unitsPerSalesUnit == null || unitsPerSalesUnit < 1 ? 1 : unitsPerSalesUnit;
    }

    /** Stock units for {@code salesUnits} of this line. */
    public int toStockUnits(int salesUnits) {
        return salesUnits * getUnitsPerSalesUnit();
    }

}
