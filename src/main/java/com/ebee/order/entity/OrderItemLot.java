package com.ebee.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Units an order line took from one stock batch. Combo lines get one row per component batch. */
@Entity
@Table(name = "order_item_lots")
public class OrderItemLot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "layer_id")
    private UUID layerId;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "returned_quantity", nullable = false)
    private Integer returnedQuantity = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected OrderItemLot() {
    }

    public OrderItemLot(OrderItem orderItem, UUID productId, UUID layerId, String lotNumber, LocalDate expiryDate,
                        int quantity, BigDecimal unitCost) {
        this.orderItem = orderItem;
        this.productId = productId;
        this.layerId = layerId;
        this.lotNumber = lotNumber;
        this.expiryDate = expiryDate;
        this.quantity = quantity;
        this.unitCost = unitCost;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** Units of this lot still with the customer. */
    public int getReturnable() {
        return quantity - returnedQuantity;
    }

    public UUID getId() { return id; }
    public OrderItem getOrderItem() { return orderItem; }
    public UUID getProductId() { return productId; }
    public UUID getLayerId() { return layerId; }
    public String getLotNumber() { return lotNumber; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public Integer getReturnedQuantity() { return returnedQuantity; }
    public void setReturnedQuantity(Integer returnedQuantity) { this.returnedQuantity = returnedQuantity; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
