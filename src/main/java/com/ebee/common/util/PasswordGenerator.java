package com.ebee.common.util;

import java.security.SecureRandom;

public final class PasswordGenerator {

    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()-_=+";

    private PasswordGenerator() {}

    public static String generate() {
        return generate(12);
    }

    public static String generate(int length) {
        if (length < 4) length = 8;
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(length);

        // Guarantee at least one from each class
        password.append(LOWERCASE.charAt(random.nextInt(LOWERCASE.length())));
        password.append(UPPERCASE.charAt(random.nextInt(UPPERCASE.length())));
        password.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        password.append(SPECIAL.charAt(random.nextInt(SPECIAL.length())));

        // Fill remaining with all characters
        String all = LOWERCASE + UPPERCASE + DIGITS + SPECIAL;
        for (int i = 4; i < length; i++) {
            password.append(all.charAt(random.nextInt(all.length())));
        }

        // Shuffle to avoid predictable prefix pattern
        char[] chars = password.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }

        return new String(chars);
    }
}
