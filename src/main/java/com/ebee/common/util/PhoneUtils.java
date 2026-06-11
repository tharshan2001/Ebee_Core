package com.ebee.common.util;

public class PhoneUtils {

    public static String normalize(String phone) {
        if (phone == null) return null;
        String cleaned = phone.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("94") && cleaned.length() > 10) {
            cleaned = "0" + cleaned.substring(2);
        } else if (!cleaned.startsWith("0")) {
            cleaned = "0" + cleaned;
        }
        return cleaned;
    }

    public static String forSms(String phone) {
        if (phone == null) return null;
        String cleaned = phone.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("0")) {
            cleaned = cleaned.substring(1);
        }
        if (!cleaned.startsWith("94")) {
            cleaned = "94" + cleaned;
        }
        return cleaned;
    }
}
