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
                .quantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .availableQuantity(availableQty)
                .reorderLevel(inventory.getReorderLevel())
                .isLowStock(isLowStock)
                .createdAt(inventory.getCreatedAt())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }
}