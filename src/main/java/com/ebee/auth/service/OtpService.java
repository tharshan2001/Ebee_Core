package com.ebee.auth.service;

import com.ebee.notification.NotificationPort;
import com.ebee.notification.model.EmailRequest;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final long OTP_TTL_SECONDS = 300;
    private static final int OTP_LENGTH = 6;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final String OTP_KEY_PREFIX = "otp:";
    private static final String OTP_ATTEMPTS_KEY_PREFIX = "otp:attempts:";

    private final StringRedisTemplate redisTemplate;
    private final NotificationPort notificationPort;
    private final UserRepository userRepository;

    public void generateAndSendOtp(String email) {
        generateAndSendOtp(email, "password reset");
    }

    public void generateAndSendOtp(String email, String purpose) {
        String otp = generateOtp();
        redisTemplate.opsForValue().set(OTP_KEY_PREFIX + email, otp, OTP_TTL_SECONDS, TimeUnit.SECONDS);

        User user = userRepository.findByEmail(email).orElse(null);
        String subject = "Ebee - OTP for " + purpose;
        var p = Map.of("otp", otp, "purpose", purpose);
        if (user != null) {
            notificationPort.notifyUser(user, subject, "otp", p, Set.of());
        } else {
            notificationPort.sendEmail(EmailRequest.builder()
                    .to(email)
                    .subject(subject)
                    .body("Your OTP for " + purpose + " is: " + otp + "\n\n"
                            + "This code expires in 5 minutes.\n\n"
                            + "If you did not request this, please ignore this email.")
                    .html(false)
                    .build());
        }

        log.info("OTP sent to {} for '{}' via email{}", email, purpose, user != null && user.getPhoneNumber() != null ? " and SMS" : "");
    }

    public boolean verifyOtp(String email, String otp) {
        String key = OTP_KEY_PREFIX + email;
        String attemptsKey = OTP_ATTEMPTS_KEY_PREFIX + email;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(attemptsKey))) {
            String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
            int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;
            if (attempts >= MAX_OTP_ATTEMPTS) {
                log.warn("OTP brute-force attempt blocked for {} after {} failed attempts", email, attempts);
                redisTemplate.delete(key);
                return false;
            }
        }

        String storedOtp = redisTemplate.opsForValue().get(key);

        if (storedOtp == null) {
            return false;
        }

        if (!storedOtp.equals(otp.trim())) {
            Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
            if (attempts != null && attempts >= MAX_OTP_ATTEMPTS) {
                redisTemplate.expire(attemptsKey, OTP_TTL_SECONDS, TimeUnit.SECONDS);
                redisTemplate.delete(key);
                log.warn("OTP invalidated for {} after {} failed attempts", email, attempts);
            }
            return false;
        }

        redisTemplate.delete(key);
        redisTemplate.delete(attemptsKey);
        return true;
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        StringBuilder otp = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }
}
