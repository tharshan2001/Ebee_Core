package com.ebee.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class DateUtils {

    private DateUtils() {}

    public static LocalDateTime toStartDateTime(LocalDate date) {
        return date != null ? date.atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
    }

    public static LocalDateTime toEndDateTime(LocalDate date) {
        return date != null ? date.plusDays(1).atStartOfDay() : LocalDateTime.of(9999, 12, 31, 23, 59);
    }
}
