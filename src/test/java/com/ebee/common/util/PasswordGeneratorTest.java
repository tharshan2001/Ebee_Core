package com.ebee.common.util;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordGeneratorTest {

    private static final String VALID_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-_=+";

    @Test
    void generate_returns12CharacterString() {
        String password = PasswordGenerator.generate();
        assertThat(password).hasSize(12);
    }

    @RepeatedTest(20)
    void generate_containsOnlyValidChars() {
        String password = PasswordGenerator.generate();
        assertThat(password.chars().allMatch(c -> VALID_CHARS.indexOf(c) >= 0)).isTrue();
    }

    @RepeatedTest(5)
    void generate_containsAtLeastOneLetter() {
        String password = PasswordGenerator.generate();
        assertThat(password).matches(".*[A-Za-z].*");
    }

    @Test
    void generate_producesDifferentValues() {
        String p1 = PasswordGenerator.generate();
        String p2 = PasswordGenerator.generate();
        assertThat(p1).isNotEqualTo(p2);
    }
}
