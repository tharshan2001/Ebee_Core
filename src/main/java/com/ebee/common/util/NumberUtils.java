package com.ebee.common.util;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Locale;

public final class NumberUtils {

    private static final DecimalFormat CURRENCY_FORMAT =
            (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);

    static {
        CURRENCY_FORMAT.setMinimumFractionDigits(2);
        CURRENCY_FORMAT.setMaximumFractionDigits(2);
        CURRENCY_FORMAT.setGroupingUsed(true);
    }

    private NumberUtils() {}

    public static String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return CURRENCY_FORMAT.format(amount);
    }

    public static Long toLong(Object obj) {
        if (obj instanceof Number n) return n.longValue();
        return 0L;
    }

    public static BigDecimal toBigDecimal(Object obj) {
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    public static LocalDateTime toLocalDateTime(Object obj) {
        if (obj instanceof Timestamp ts) return ts.toLocalDateTime();
        if (obj instanceof LocalDateTime ldt) return ldt;
        return null;
    }
}
