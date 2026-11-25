package com.ebee.pricing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** A product sold by the pack: {@code unitsPerPack} stock units, optionally at a fixed pack price. */
@Entity
@Table(name = "product_units")
public class ProductUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;
    @Column(name = "units_per_pack", nullable = false)
    private int unitsPerPack;
    /** Fixed pack price; null = per-unit price × unitsPerPack. */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected ProductUnit() {
    }

    public ProductUnit(UUID productId, Unit unit, int unitsPerPack, BigDecimal price) {
        this.productId = productId;
        this.unit = unit;
        this.unitsPerPack = unitsPerPack;
        this.price = price;
    }

    /** e.g. "Box of 12". */
    public String label() {
        return unit.getName() + " of " + unitsPerPack;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public Unit getUnit() { return unit; }
    public int getUnitsPerPack() { return unitsPerPack; }
    public void setUnitsPerPack(int unitsPerPack) { this.unitsPerPack = unitsPerPack; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
