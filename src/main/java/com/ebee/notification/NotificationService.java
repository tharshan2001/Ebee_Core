package com.ebee.notification;

import com.ebee.agent.entity.Agent;
import com.ebee.customer.entity.Customer;
import com.ebee.notification.model.EmailRequest;
import com.ebee.notification.model.NotificationResult;
import com.ebee.notification.model.SmsRequest;
import com.ebee.notification.sender.EmailSender;
import com.ebee.notification.sender.SmsSender;
import com.ebee.notification.template.NotificationTemplateService;
import com.ebee.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final NotificationTemplateService templateService;

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

    public void notifyUser(User user, String subject, String message) {
        if (user.getEmail() != null) {
            EmailRequest emailReq = EmailRequest.builder()
                    .to(user.getEmail())
                    .subject(subject)
                    .body(message)
                    .html(false)
                    .build();
            NotificationResult result = emailSender.send(emailReq);
            if (!result.isSuccess()) {
                log.warn("Failed to email user {}: {}", user.getEmail(), result.getMessage());
            }
        }

        if (user.getPhoneNumber() != null) {
            SmsRequest smsReq = SmsRequest.builder()
                    .phoneNumbers(List.of(user.getPhoneNumber()))
                    .message(message)
                    .build();
            NotificationResult result = smsSender.send(smsReq);
            if (!result.isSuccess()) {
                log.warn("Failed to SMS user {}: {}", user.getPhoneNumber(), result.getMessage());
            }
        }
    }

    public void sendHtmlEmail(String to, String subject, String template, Map<String, String> placeholders) {
        String body = templateService.resolve(template, placeholders);
        if (body.isEmpty()) {
            log.warn("Empty template body for {}", template);
            return;
        }
        EmailRequest emailReq = EmailRequest.builder()
                .to(to)
                .subject(subject)
                .body(body)
                .html(true)
                .build();
        emailSender.send(emailReq);
    }

    public void notifyCustomer(Customer customer, String subject, String message) {
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
                    .subject(subject)
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
