package com.ebee.auth.service;

import com.ebee.notification.NotificationService;
import com.ebee.notification.model.EmailRequest;
import com.ebee.notification.model.SmsRequest;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final long OTP_TTL_SECONDS = 300;
    private static final int OTP_LENGTH = 6;

    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public void generateAndSendOtp(String email) {
        generateAndSendOtp(email, "password reset");
    }

    public void generateAndSendOtp(String email, String purpose) {
        String otp = generateOtp();
        otpStore.put(email, new OtpEntry(otp, LocalDateTime.now().plusSeconds(OTP_TTL_SECONDS)));

        User user = userRepository.findByEmail(email).orElse(null);
        String phoneNumber = user != null ? user.getPhoneNumber() : null;

        String subject = "Ebee - OTP for " + purpose;
        String body = "Your OTP for " + purpose + " is: " + otp + "\n\n"
                + "This code expires in 5 minutes.\n\n"
                + "If you did not request this, please ignore this email.";

        notificationService.sendEmail(EmailRequest.builder()
                .to(email)
                .subject(subject)
                .body(body)
                .html(false)
                .build());

        if (phoneNumber != null) {
            String smsMessage = "Ebee: Your OTP for " + purpose + " is " + otp + ". Expires in 5 minutes.";
            notificationService.sendSms(SmsRequest.builder()
                    .phoneNumbers(List.of(phoneNumber))
                    .message(smsMessage)
                    .build());
        }

        log.info("OTP sent to {} for '{}' via email{}", email, purpose, phoneNumber != null ? " and SMS" : "");
    }

    public boolean verifyOtp(String email, String otp) {
        OtpEntry entry = otpStore.get(email);

        if (entry == null) {
            return false;
        }

        if (entry.expiry().isBefore(LocalDateTime.now())) {
            otpStore.remove(email);
            return false;
        }

        if (!entry.otp().equals(otp.trim())) {
            return false;
        }

        otpStore.remove(email);
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

    private record OtpEntry(String otp, LocalDateTime expiry) {}
}
