package com.ebee.auth.security;

import com.ebee.auth.config.RateLimitingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingServicePasswordResetTest {

    private RateLimitingService service;

    @BeforeEach
    void setUp() {
        // 3 per email, 5 per IP, 15-minute window
        service = new RateLimitingService(new RateLimitingProperties(null, null, null,
                new RateLimitingProperties.PasswordReset(3, 5, 15)));
    }

    @Test
    void blocksFourthRequestForSameEmail_caseInsensitive() {
        assertThat(service.isPasswordResetAllowed("User@Ebee.com", "203.0.113.1")).isTrue();
        assertThat(service.isPasswordResetAllowed("user@ebee.com ", "203.0.113.2")).isTrue();
        assertThat(service.isPasswordResetAllowed("USER@EBEE.COM", "203.0.113.3")).isTrue();
        assertThat(service.isPasswordResetAllowed("user@ebee.com", "203.0.113.4")).isFalse();
    }

    @Test
    void blocksOneIpSprayingManyEmails() {
        for (int i = 0; i < 5; i++) {
            assertThat(service.isPasswordResetAllowed("user" + i + "@ebee.com", "203.0.113.9")).isTrue();
        }
        assertThat(service.isPasswordResetAllowed("user99@ebee.com", "203.0.113.9")).isFalse();
    }

    @Test
    void limitsAreIndependentAcrossEmailsAndIps() {
        for (int i = 0; i < 3; i++) {
            service.isPasswordResetAllowed("a@ebee.com", "203.0.113.1");
        }
        assertThat(service.isPasswordResetAllowed("b@ebee.com", "203.0.113.2")).isTrue();
    }
}
