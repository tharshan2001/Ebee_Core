package com.ebee.location.entity;

import com.ebee.product.entity.Product;
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

@Entity
@Table(name = "stock_transfer_items")
public class StockTransferItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private StockTransfer transfer;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    /** FIFO cost of the units taken from the source, set on dispatch. */
    @Column(name = "total_cost", precision = 14, scale = 4)
    private BigDecimal totalCost;

    protected StockTransferItem() {
    }

    public StockTransferItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    /** Cost per unit carried to the destination (null before dispatch). */
    public BigDecimal getUnitCost() {
        if (totalCost == null || quantity == null || quantity == 0) {
            return null;
        }
        return totalCost.divide(BigDecimal.valueOf(quantity), 4, java.math.RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public StockTransfer getTransfer() { return transfer; }
    void setTransfer(StockTransfer transfer) { this.transfer = transfer; }
    public Product getProduct() { return product; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
}
