package com.ebee.report.dto;

import java.time.LocalDate;

public record PeriodRange(LocalDate startDate, LocalDate endDate) {
    public static final PeriodRange ALL_TIME = new PeriodRange(
            LocalDate.of(2020, 1, 1), LocalDate.now());

    public PeriodRange {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must be before or equal to endDate");
        }
    }
}
