package com.ebee.stock.dto;

import com.ebee.stock.enums.StockMovementType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class StockRequest {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Quantity change is required")
    private Integer quantityChange;

    @NotNull(message = "Movement type is required")
    private StockMovementType movementType;

    private String notes;
}
