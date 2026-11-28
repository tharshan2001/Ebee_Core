package com.ebee.common.util;

import java.util.Locale;

/** Turns what someone typed into a lower-case SQL LIKE pattern. */
public final class SearchPatterns {

    private SearchPatterns() {
    }

    public static String contains(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        // Typed % or _ stay wildcards: harmless in a name search, and no escape clause is needed.
        return "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
