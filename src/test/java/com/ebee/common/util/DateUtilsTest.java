package com.ebee.common.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DateUtilsTest {

    @Test
    void toStartDateTime_returnsStartOfDay() {
        LocalDate date = LocalDate.of(2025, 6, 1);
        LocalDateTime result = DateUtils.toStartDateTime(date);
        assertThat(result).isEqualTo(LocalDateTime.of(2025, 6, 1, 0, 0));
    }

    @Test
    void toStartDateTime_null_returnsEpoch() {
        assertThat(DateUtils.toStartDateTime(null))
                .isEqualTo(LocalDateTime.of(1970, 1, 1, 0, 0));
    }

    @Test
    void toEndDateTime_returnsStartOfNextDay() {
        LocalDate date = LocalDate.of(2025, 6, 1);
        LocalDateTime result = DateUtils.toEndDateTime(date);
        assertThat(result).isEqualTo(LocalDateTime.of(2025, 6, 2, 0, 0));
    }

    @Test
    void toEndDateTime_null_returnsFarFuture() {
        assertThat(DateUtils.toEndDateTime(null))
                .isEqualTo(LocalDateTime.of(9999, 12, 31, 23, 59));
    }
}
