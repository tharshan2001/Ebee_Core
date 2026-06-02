package com.ebee.notification;

import com.ebee.agent.entity.Agent;
import com.ebee.customer.entity.Customer;
import com.ebee.notification.model.EmailRequest;
import com.ebee.notification.model.NotificationResult;
import com.ebee.notification.model.SmsRequest;
import com.ebee.notification.sender.EmailSender;
import com.ebee.notification.sender.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailSender emailSender;
    private final SmsSender smsSender;

    public NotificationResult sendEmail(EmailRequest request) {
        return emailSender.send(request);
    }

    public NotificationResult sendSms(SmsRequest request) {
        return smsSender.send(request);
    }

    public void notifyAgent(Agent agent, String subject, String message) {
        if (agent.getUser() != null && agent.getUser().getEmail() != null) {
            EmailRequest emailReq = EmailRequest.builder()
                    .to(agent.getUser().getEmail())
                    .subject(subject)
                    .body(message)
                    .html(false)
                    .build();
            NotificationResult result = emailSender.send(emailReq);
            if (!result.isSuccess()) {
                log.warn("Failed to email agent {}: {}", agent.getAgentCode(), result.getMessage());
            }
        }

        if (agent.getPhoneNumber() != null) {
            SmsRequest smsReq = SmsRequest.builder()
                    .phoneNumbers(List.of(agent.getPhoneNumber()))
                    .message(message)
                    .build();
            NotificationResult result = smsSender.send(smsReq);
            if (!result.isSuccess()) {
                log.warn("Failed to SMS agent {}: {}", agent.getAgentCode(), result.getMessage());
            }
        }
    }

    public void notifyCustomer(Customer customer, String message) {
        if (customer.getPhoneNumber() != null) {
            SmsRequest smsReq = SmsRequest.builder()
                    .phoneNumbers(List.of(customer.getPhoneNumber()))
                    .message(message)
                    .build();
            NotificationResult result = smsSender.send(smsReq);
            if (!result.isSuccess()) {
                log.warn("Failed to SMS customer {}: {}", customer.getPhoneNumber(), result.getMessage());
            }
        }

        if (customer.getEmail() != null) {
            EmailRequest emailReq = EmailRequest.builder()
                    .to(customer.getEmail())
                    .subject("Ebee Notification")
                    .body(message)
                    .html(false)
                    .build();
            NotificationResult result = emailSender.send(emailReq);
            if (!result.isSuccess()) {
                log.warn("Failed to email customer {}: {}", customer.getEmail(), result.getMessage());
            }
        }
    }
}
