package com.ebee.inventory.dto;

import com.ebee.inventory.entity.InventoryLayer;

import java.math.BigDecimal;
import java.util.UUID;

/** Units taken from one batch (layer) by a stock deduction. */
public record LotAllocation(UUID productId, InventoryLayer layer, int quantity, BigDecimal unitCost) {
    public BigDecimal cost() {
        return unitCost.multiply(BigDecimal.valueOf(quantity));
    }
}
