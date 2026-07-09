package com.ebee.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record InventoryLayerResponse(
    UUID id,
    UUID inventoryId,
    Integer quantity,
    Integer remainingQuantity,
    BigDecimal unitCost,
    LocalDateTime receivedAt
) {}
