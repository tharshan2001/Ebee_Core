package com.ebee.auth.service;

import com.ebee.notification.NotificationPort;
import com.ebee.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private NotificationPort notificationPort;
    @Mock private UserRepository userRepository;

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(redisTemplate, notificationPort, userRepository);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void verifyOtp_validOtp_returnsTrue() {
        String email = "user@ebee.com";
        when(redisTemplate.hasKey("otp:attempts:" + email)).thenReturn(false);
        when(valueOps.get("otp:" + email)).thenReturn(sha256Hex("123456"));

        boolean result = otpService.verifyOtp(email, "123456");

        assertThat(result).isTrue();
        verify(redisTemplate).delete("otp:" + email);
        verify(redisTemplate).delete("otp:attempts:" + email);
    }

    @Test
    void verifyOtp_wrongOtp_returnsFalseAndIncrementsAttempts() {
        String email = "user@ebee.com";
        when(redisTemplate.hasKey("otp:attempts:" + email)).thenReturn(false);
        when(valueOps.get("otp:" + email)).thenReturn(sha256Hex("123456"));
        when(valueOps.increment("otp:attempts:" + email)).thenReturn(1L);

        boolean result = otpService.verifyOtp(email, "000000");

        assertThat(result).isFalse();
        verify(valueOps).increment("otp:attempts:" + email);
        verify(redisTemplate, never()).delete("otp:" + email);
    }

    @Test
    void verifyOtp_wrongOtp_reachesMaxAttempts_invalidatesOtp() {
        String email = "user@ebee.com";
        when(redisTemplate.hasKey("otp:attempts:" + email)).thenReturn(false);
        when(valueOps.get("otp:" + email)).thenReturn(sha256Hex("123456"));
        when(valueOps.increment("otp:attempts:" + email)).thenReturn(5L);

        boolean result = otpService.verifyOtp(email, "000000");

        assertThat(result).isFalse();
        verify(valueOps).increment("otp:attempts:" + email);
        verify(redisTemplate).expire(eq("otp:attempts:" + email), anyLong(), eq(TimeUnit.SECONDS));
        verify(redisTemplate).delete("otp:" + email);
    }

    @Test
    void verifyOtp_alreadyAtMaxAttempts_blocksImmediately() {
        String email = "user@ebee.com";
        when(redisTemplate.hasKey("otp:attempts:" + email)).thenReturn(true);
        when(valueOps.get("otp:attempts:" + email)).thenReturn("5");

        boolean result = otpService.verifyOtp(email, "123456");

        assertThat(result).isFalse();
        verify(valueOps, never()).get("otp:" + email);
        verify(redisTemplate).delete("otp:" + email);
    }

    @Test
    void verifyOtp_underMaxAttempts_stillAllowsVerification() {
        String email = "user@ebee.com";
        when(redisTemplate.hasKey("otp:attempts:" + email)).thenReturn(true);
        when(valueOps.get("otp:attempts:" + email)).thenReturn("3");
        when(valueOps.get("otp:" + email)).thenReturn(sha256Hex("123456"));

        boolean result = otpService.verifyOtp(email, "123456");

        assertThat(result).isTrue();
        verify(redisTemplate).delete("otp:" + email);
        verify(redisTemplate).delete("otp:attempts:" + email);
    }

    @Test
    void verifyOtp_noStoredOtp_returnsFalse() {
        String email = "user@ebee.com";
        when(redisTemplate.hasKey("otp:attempts:" + email)).thenReturn(false);
        when(valueOps.get("otp:" + email)).thenReturn(null);

        boolean result = otpService.verifyOtp(email, "123456");

        assertThat(result).isFalse();
        verify(valueOps, never()).increment(anyString());
    }
}
