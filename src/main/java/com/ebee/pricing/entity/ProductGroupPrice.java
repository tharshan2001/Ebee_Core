package com.ebee.pricing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** A product's price (per stock unit) for one price group. */
@Entity
@Table(name = "product_group_prices")
public class ProductGroupPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(name = "price_group_id", nullable = false)
    private UUID priceGroupId;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    protected ProductGroupPrice() {
    }

    public ProductGroupPrice(UUID productId, UUID priceGroupId, BigDecimal price) {
        this.productId = productId;
        this.priceGroupId = priceGroupId;
        this.price = price;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public UUID getPriceGroupId() { return priceGroupId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
