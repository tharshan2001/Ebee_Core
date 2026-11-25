package com.ebee.stock.enums;

public enum StockMovementType {
    IN,
    OUT,
    ADJUSTMENT,
    RETURN,
    DAMAGE,
    PURCHASE,
    RESERVE,
    RELEASE,
    /** Leaving a location on a stock transfer. */
    TRANSFER_OUT,
    /** Arriving at a location from a stock transfer. */
    TRANSFER_IN,
    /** Expired stock written off. */
    EXPIRED
}
