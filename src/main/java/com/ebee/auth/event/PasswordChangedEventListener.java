package com.ebee.auth.event;

import com.ebee.notification.NotificationPort;
import com.ebee.notification.model.EmailRequest;
import com.ebee.notification.model.SmsRequest;
import com.ebee.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordChangedEventListener {

    private final NotificationPort notificationPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordChanged(PasswordChangedEvent event) {
        User user = event.getUser();
        try {
            String time = LocalDateTime.now().toString();
            String subject = "Ebee - Password Changed";

            notificationPort.sendEmail(EmailRequest.builder()
                    .to(user.getEmail())
                    .subject(subject)
                    .body("Your Ebee account password was changed.\n\nTime: " + time
                            + "\n\nIf you did not make this change, please contact support immediately.")
                    .html(false)
                    .build());

            if (user.getPhoneNumber() != null) {
                notificationPort.sendSms(SmsRequest.builder()
                        .phoneNumbers(List.of(user.getPhoneNumber()))
                        .message("Ebee: Your account password was changed at " + time
                                + ". If this was not you, contact support.")
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to send password changed alert to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }
}
