package com.ebee.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "null, null",
            "'', '0'",
            "'+94771234567', '0771234567'",
            "'94771234567', '0771234567'",
            "'0771234567', '0771234567'",
            "'0712345678', '0712345678'",
            "'+94 77 123 4567', '0771234567'",
            "'94-77-1234-567', '0771234567'",
    })
    void normalize_returnsExpected(String input, String expected) {
        String normalized = PhoneUtils.normalize("null".equals(input) ? null : input);
        if ("null".equals(input)) {
            assertThat(normalized).isNull();
        } else {
            assertThat(normalized).isEqualTo(expected);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "'0771234567', '94771234567'",
            "'0712345678', '94712345678'",
            "null, null",
    })
    void forSms_returnsExpected(String input, String expected) {
        String result = PhoneUtils.forSms("null".equals(input) ? null : input);
        if ("null".equals(input)) {
            assertThat(result).isNull();
        } else {
            assertThat(result).isEqualTo(expected);
        }
    }
}
