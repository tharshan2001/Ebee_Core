package com.ebee.report.util;

import com.ebee.report.dto.PeriodRange;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;

public final class PeriodUtils {

    private PeriodUtils() {}

    public static PeriodRange resolve(String period, LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return new PeriodRange(startDate, endDate);
        }
        if (period == null || period.isBlank()) {
            return PeriodRange.ALL_TIME;
        }
        LocalDate now = LocalDate.now();
        return switch (period.toLowerCase()) {
            case "today" -> new PeriodRange(now, now);
            case "yesterday" -> new PeriodRange(now.minusDays(1), now.minusDays(1));
            case "this_week" -> new PeriodRange(now.with(DayOfWeek.MONDAY), now);
            case "last_week" -> new PeriodRange(
                    now.with(DayOfWeek.MONDAY).minusWeeks(1),
                    now.with(DayOfWeek.MONDAY).minusDays(1));
            case "this_month" -> new PeriodRange(now.withDayOfMonth(1), now);
            case "last_month" -> new PeriodRange(
                    now.minusMonths(1).withDayOfMonth(1),
                    now.withDayOfMonth(1).minusDays(1));
            case "this_quarter" -> new PeriodRange(
                    now.with(IsoFields.DAY_OF_QUARTER, 1), now);
            case "last_quarter" -> {
                LocalDate qStart = now.with(IsoFields.DAY_OF_QUARTER, 1);
                LocalDate prevQStart = qStart.minusMonths(3);
                yield new PeriodRange(prevQStart, qStart.minusDays(1));
            }
            case "this_year" -> new PeriodRange(now.withDayOfYear(1), now);
            case "last_year" -> new PeriodRange(
                    now.minusYears(1).withDayOfYear(1),
                    now.withDayOfYear(1).minusDays(1));
            case "all" -> PeriodRange.ALL_TIME;
            default -> throw new IllegalArgumentException("Unknown period: " + period);
        };
    }

    public static PeriodRange previousPeriod(PeriodRange current) {
        long days = current.endDate().toEpochDay() - current.startDate().toEpochDay() + 1;
        return new PeriodRange(
                current.startDate().minusDays(days),
                current.startDate().minusDays(1));
    }
}
