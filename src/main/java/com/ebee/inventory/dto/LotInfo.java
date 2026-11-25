package com.ebee.inventory.dto;

import java.time.LocalDate;

/** Lot details for stock coming in (all optional unless the product tracks expiry). */
public record LotInfo(String lotNumber, LocalDate expiryDate, LocalDate manufacturedDate) {
    public static final LotInfo NONE = new LotInfo(null, null, null);
}
