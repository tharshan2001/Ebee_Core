package com.ebee.common.util;

public final class PhoneUtils {

    private PhoneUtils() {
    }

    public static String normalize(String phone) {
        if (phone == null) return null;
        String cleaned = phone.replaceAll("[^0-9+]", "");
        if (cleaned.startsWith("+94")) {
            cleaned = "0" + cleaned.substring(3);
        } else if (cleaned.startsWith("0094")) {
            cleaned = "0" + cleaned.substring(4);
        } else if (cleaned.startsWith("94")) {
            cleaned = "0" + cleaned.substring(2);
        }
        while (cleaned.startsWith("00")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) {
            cleaned = "0";
        } else if (!cleaned.startsWith("0")) {
            cleaned = "0" + cleaned;
        }
        return cleaned;
    }

    /**
     * A contact phone that may be local or foreign (suppliers). Sri Lankan numbers are stored as
     * 0XXXXXXXXX, including ones that lost the leading 0 in a spreadsheet; others are kept as typed.
     *
     * @throws IllegalArgumentException if it isn't a phone number
     */
    public static String contactPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String trimmed = phone.trim();
        String digits = trimmed.replaceAll("[^0-9+]", "");
        if (!trimmed.matches("[+0-9 ()\\-]+") || digits.length() < 7 || trimmed.length() > 20) {
            throw new IllegalArgumentException("'" + trimmed + "' isn't a phone number");
        }
        if (digits.matches("(\\+94|0094|94)\\d{9}")) return "0" + digits.substring(digits.length() - 9);
        if (digits.matches("[1-9]\\d{8}")) return "0" + digits;
        if (digits.matches("0\\d{9}")) return digits;
        return trimmed;
    }

    public static String forSms(String phone) {
        if (phone == null) return null;
        String cleaned = phone.replaceAll("[^0-9+]", "");
        if (cleaned.startsWith("+94")) {
            cleaned = cleaned.substring(3);
        } else if (cleaned.startsWith("0094")) {
            cleaned = cleaned.substring(4);
        } else if (cleaned.startsWith("94")) {
            cleaned = cleaned.substring(2);
        } else if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.startsWith("0")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.startsWith("00")) {
            cleaned = cleaned.substring(1);
        }
        if (!cleaned.startsWith("94")) {
            cleaned = "94" + cleaned;
        }
        return cleaned;
    }
}
