package com.ebee.stock.dto;

import com.ebee.stock.enums.StockMovementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class StockRequest {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Quantity change is required")
    private Integer quantityChange;

    @NotNull(message = "Movement type is required")
    private StockMovementType movementType;

    @NotBlank(message = "Reason/notes is required for stock adjustment")
    private String notes;

    /** Location to adjust; null = default location. */
    private java.util.UUID locationId;

    /** Cost per unit for stock coming in; null = the current average cost. */
    @PositiveOrZero(message = "Unit cost must be zero or positive")
    private BigDecimal unitCost;

    /** Batch details for stock coming in; products that track expiry need the expiry date. */
    @Size(max = 50, message = "Lot number must not exceed 50 characters")
    private String lotNumber;

    private LocalDate expiryDate;

    private LocalDate manufacturedDate;
}
