package com.ebee.common.util;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public final class NumberUtils {

    private NumberUtils() {}

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
