package com.ebee.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationUtilsTest {

    @Test
    void safePage_zero_returnsZero() {
        assertThat(PaginationUtils.safePage(0)).isEqualTo(0);
    }

    @Test
    void safePage_positive_returnsSame() {
        assertThat(PaginationUtils.safePage(5)).isEqualTo(5);
    }

    @Test
    void safePage_negative_collapsesToZero() {
        assertThat(PaginationUtils.safePage(-3)).isEqualTo(0);
    }

    @Test
    void safeSize_normal_returnsSame() {
        assertThat(PaginationUtils.safeSize(20)).isEqualTo(20);
    }

    @Test
    void safeSize_atMax_returnsSame() {
        assertThat(PaginationUtils.safeSize(PaginationUtils.MAX_PAGE_SIZE)).isEqualTo(200);
    }

    @Test
    void safeSize_aboveMax_capsAtMax() {
        assertThat(PaginationUtils.safeSize(10_000_000)).isEqualTo(PaginationUtils.MAX_PAGE_SIZE);
    }

    @Test
    void safeSize_zeroOrNegative_usesDefault() {
        assertThat(PaginationUtils.safeSize(0)).isEqualTo(PaginationUtils.DEFAULT_PAGE_SIZE);
        assertThat(PaginationUtils.safeSize(-1)).isEqualTo(PaginationUtils.DEFAULT_PAGE_SIZE);
    }
}
