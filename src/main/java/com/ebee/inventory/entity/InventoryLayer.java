package com.ebee.inventory.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_layers")
public class InventoryLayer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "expiry_date")
    private java.time.LocalDate expiryDate;

    @Column(name = "manufactured_date")
    private java.time.LocalDate manufacturedDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public InventoryLayer() {}

    public InventoryLayer(Inventory inventory, Integer quantity, Integer remainingQuantity, BigDecimal unitCost, LocalDateTime receivedAt) {
        this.inventory = inventory;
        this.quantity = quantity;
        this.remainingQuantity = remainingQuantity;
        this.unitCost = unitCost;
        this.receivedAt = receivedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(Integer remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }
    public java.time.LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(java.time.LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public java.time.LocalDate getManufacturedDate() { return manufacturedDate; }
    public void setManufacturedDate(java.time.LocalDate manufacturedDate) { this.manufacturedDate = manufacturedDate; }

    /** True when the batch has an expiry date before {@code today}. */
    public boolean isExpired(java.time.LocalDate today) {
        return expiryDate != null && expiryDate.isBefore(today);
    }
}
