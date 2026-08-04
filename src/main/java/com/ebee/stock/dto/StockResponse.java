package com.ebee.stock.dto;

import com.ebee.stock.enums.StockMovementType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class StockResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private String productItemCode;
    private Integer quantityChange;
    private Integer quantityBefore;
    private Integer quantityAfter;
    private Integer reservedQuantityBefore;
    private Integer reservedQuantityAfter;
    private StockMovementType movementType;
    private String notes;
    private UUID createdById;
    private String createdByName;
    private String createdByEmail;
    private LocalDateTime createdAt;
}
