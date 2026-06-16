package com.ebee.common.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscountTypeTest {

    @Test
    void fromString_validUppercase_returnsEnum() {
        assertThat(DiscountType.fromString("PERCENTAGE")).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(DiscountType.fromString("FIXED")).isEqualTo(DiscountType.FIXED);
    }

    @Test
    void fromString_lowercase_returnsEnum() {
        assertThat(DiscountType.fromString("percentage")).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(DiscountType.fromString("fixed")).isEqualTo(DiscountType.FIXED);
    }

    @Test
    void fromString_mixedCaseAndWhitespace_returnsEnum() {
        assertThat(DiscountType.fromString("  Percentage ")).isEqualTo(DiscountType.PERCENTAGE);
    }

    @Test
    void fromString_null_returnsNull() {
        assertThat(DiscountType.fromString(null)).isNull();
    }

    @Test
    void fromString_unknown_throwsWithMessage() {
        assertThatThrownBy(() -> DiscountType.fromString("BANANA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PERCENTAGE or FIXED")
                .hasMessageContaining("BANANA");
    }
}
