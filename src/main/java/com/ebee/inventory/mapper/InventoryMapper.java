package com.ebee.inventory.mapper;

import com.ebee.inventory.dto.InventoryResponse;
import com.ebee.inventory.entity.Inventory;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    public InventoryResponse toResponse(Inventory inventory) {
        if (inventory == null) {
            return null;
        }

        Integer availableQty = inventory.getQuantity() - inventory.getReservedQuantity();
        boolean isLowStock = inventory.getReorderLevel() != null && availableQty <= inventory.getReorderLevel();

        return InventoryResponse.builder()
                .id(inventory.getId())
                .productId(inventory.getProduct().getId())
                .productName(inventory.getProduct().getName())
                .productItemCode(inventory.getProduct().getItemCode())
                .locationId(inventory.getLocation() != null ? inventory.getLocation().getId() : null)
                .locationName(inventory.getLocation() != null ? inventory.getLocation().getName() : null)
                .quantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .availableQuantity(availableQty)
                .reorderLevel(inventory.getReorderLevel())
                .averageCost(inventory.getAverageCost())
                .isLowStock(isLowStock)
                .createdAt(inventory.getCreatedAt())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }

    /**
     * One summary across every location (what product screens show). Average cost is weighted by
     * quantity; low stock if any location is at or below its reorder level.
     */
    public InventoryResponse toProductSummary(java.util.Collection<Inventory> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        if (rows.size() == 1) {
            return toResponse(rows.iterator().next());
        }
        Inventory any = rows.iterator().next();
        int qty = 0;
        int reserved = 0;
        boolean low = false;
        java.math.BigDecimal value = java.math.BigDecimal.ZERO;
        java.time.LocalDateTime updated = null;
        for (Inventory i : rows) {
            qty += i.getQuantity();
            reserved += i.getReservedQuantity();
            value = value.add(i.getAverageCost().multiply(java.math.BigDecimal.valueOf(i.getQuantity())));
            int available = i.getQuantity() - i.getReservedQuantity();
            low |= i.getReorderLevel() != null && available <= i.getReorderLevel();
            if (updated == null || (i.getUpdatedAt() != null && i.getUpdatedAt().isAfter(updated))) {
                updated = i.getUpdatedAt();
            }
        }
        return InventoryResponse.builder()
                .productId(any.getProduct().getId())
                .productName(any.getProduct().getName())
                .productItemCode(any.getProduct().getItemCode())
                .locationName("All locations")
                .quantity(qty)
                .reservedQuantity(reserved)
                .availableQuantity(qty - reserved)
                .averageCost(qty > 0 ? value.divide(java.math.BigDecimal.valueOf(qty), 4, java.math.RoundingMode.HALF_UP)
                        : java.math.BigDecimal.ZERO)
                .isLowStock(low)
                .updatedAt(updated)
                .build();
    }
}
