package com.ebee.combo.enums;

public enum ComboRuleType {
    FREE_PRODUCT,
    DISCOUNT;

    public static ComboRuleType fromString(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return ComboRuleType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "ComboRuleType must be FREE_PRODUCT or DISCOUNT (got '" + raw + "')");
        }
    }
}
