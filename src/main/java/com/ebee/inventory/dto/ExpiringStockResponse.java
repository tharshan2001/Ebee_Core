package com.ebee.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A batch that has expired or expires soon. daysLeft is negative once expired. */
public record ExpiringStockResponse(UUID layerId, UUID productId, String productName, String productItemCode,
                                    UUID locationId, String locationName, String lotNumber, LocalDate expiryDate,
                                    long daysLeft, int remainingQuantity, BigDecimal unitCost, BigDecimal value) {
}
