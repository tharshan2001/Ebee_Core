package com.ebee.location.dto;

import com.ebee.location.entity.StockTransfer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StockTransferResponse(UUID id, String transferNumber, UUID fromLocationId, String fromLocationName,
                                    UUID toLocationId, String toLocationName, StockTransfer.Status status,
                                    String notes, LocalDateTime createdAt, LocalDateTime dispatchedAt,
                                    LocalDateTime receivedAt, LocalDateTime cancelledAt,
                                    BigDecimal totalCost, List<Item> items) {

    public record Item(UUID productId, String productName, String productItemCode, int quantity,
                       BigDecimal unitCost, BigDecimal totalCost) {
    }

    public static StockTransferResponse of(StockTransfer t) {
        List<Item> items = t.getItems().stream().map(i -> new Item(i.getProduct().getId(), i.getProduct().getName(),
                i.getProduct().getItemCode(), i.getQuantity(), i.getUnitCost(), i.getTotalCost())).toList();
        BigDecimal total = t.getItems().stream().map(i -> i.getTotalCost() == null ? BigDecimal.ZERO : i.getTotalCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StockTransferResponse(t.getId(), t.getTransferNumber(), t.getFromLocation().getId(),
                t.getFromLocation().getName(), t.getToLocation().getId(), t.getToLocation().getName(), t.getStatus(),
                t.getNotes(), t.getCreatedAt(), t.getDispatchedAt(), t.getReceivedAt(), t.getCancelledAt(), total, items);
    }
}
