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
