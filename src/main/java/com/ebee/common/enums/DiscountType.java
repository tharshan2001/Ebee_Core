package com.ebee.common.enums;

/**
 * Discount type used in product catalog discounts and per-order admin
 * discounts. Stored as VARCHAR in the database (with a CHECK
 * constraint) but exposed as a strongly-typed enum at the API and
 * service layer to prevent arbitrary strings like "BANANA" from
 * being accepted.
 */
public enum DiscountType {
    PERCENTAGE,
    FIXED;

    /**
     * Case-insensitive parse that maps a stored string (possibly
     * uppercase) to the enum. Throws {@link IllegalArgumentException}
     * with a clear message for unknown values.
     */
    public static DiscountType fromString(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return DiscountType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "discountType must be PERCENTAGE or FIXED (got '" + raw + "')");
        }
    }
}
