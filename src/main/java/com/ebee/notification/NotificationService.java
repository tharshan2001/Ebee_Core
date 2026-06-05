package com.ebee.notification;

import com.ebee.agent.entity.Agent;
import com.ebee.alert.entity.Alert;
import com.ebee.alert.enums.AlertType;
import com.ebee.alert.repository.AlertRepository;
import com.ebee.customer.entity.Customer;
import com.ebee.notification.model.EmailRequest;
import com.ebee.notification.model.SmsRequest;
import com.ebee.notification.sender.EmailSender;
import com.ebee.notification.sender.SmsSender;
import com.ebee.notification.template.NotificationTemplateService;
import com.ebee.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final NotificationTemplateService templateService;
    private final AlertRepository alertRepository;

    @Async
    public void sendEmail(EmailRequest request) {
        try {
            emailSender.send(request);
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", request.getTo(), e.getMessage());
        }
    }

    @Async
    public void sendSms(SmsRequest request) {
        try {
            smsSender.send(request);
        } catch (Exception e) {
            log.warn("Failed to send SMS to {}: {}", request.getPhoneNumbers(), e.getMessage());
        }
    }

    @Async
    public void notifyAgent(Agent agent, String subject, String message) {
        try {
            if (agent.getUser() != null && agent.getUser().getEmail() != null) {
                EmailRequest emailReq = EmailRequest.builder()
                        .to(agent.getUser().getEmail())
                        .subject(subject)
                        .body(message)
                        .html(false)
                        .build();
                emailSender.send(emailReq);
            }
        } catch (Exception e) {
            log.warn("Failed to email agent {}: {}", agent.getAgentCode(), e.getMessage());
        }

        try {
            if (agent.getPhoneNumber() != null) {
                SmsRequest smsReq = SmsRequest.builder()
                        .phoneNumbers(List.of(agent.getPhoneNumber()))
                        .message(message)
                        .build();
                smsSender.send(smsReq);
            }
        } catch (Exception e) {
            log.warn("Failed to SMS agent {}: {}", agent.getAgentCode(), e.getMessage());
        }
    }

    @Async
    public void notifyUser(User user, String subject, String message) {
        try {
            if (user.getEmail() != null) {
                EmailRequest emailReq = EmailRequest.builder()
                        .to(user.getEmail())
                        .subject(subject)
                        .body(message)
                        .html(false)
                        .build();
                emailSender.send(emailReq);
            }
        } catch (Exception e) {
            log.warn("Failed to email user {}: {}", user.getEmail(), e.getMessage());
        }

        try {
            if (user.getPhoneNumber() != null) {
                SmsRequest smsReq = SmsRequest.builder()
                        .phoneNumbers(List.of(user.getPhoneNumber()))
                        .message(message)
                        .build();
                smsSender.send(smsReq);
            }
        } catch (Exception e) {
            log.warn("Failed to SMS user {}: {}", user.getPhoneNumber(), e.getMessage());
        }
    }

    @Async
    public void sendHtmlEmail(String to, String subject, String template, Map<String, String> placeholders) {
        try {
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
        } catch (Exception e) {
            log.warn("Failed to send HTML email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void notifyCustomer(Customer customer, String subject, String message) {
        try {
            if (customer.getPhoneNumber() != null) {
                SmsRequest smsReq = SmsRequest.builder()
                        .phoneNumbers(List.of(customer.getPhoneNumber()))
                        .message(message)
                        .build();
                smsSender.send(smsReq);
            }
        } catch (Exception e) {
            log.warn("Failed to SMS customer {}: {}", customer.getPhoneNumber(), e.getMessage());
        }

        try {
            if (customer.getEmail() != null) {
                EmailRequest emailReq = EmailRequest.builder()
                        .to(customer.getEmail())
                        .subject(subject)
                        .body(message)
                        .html(false)
                        .build();
                emailSender.send(emailReq);
            }
        } catch (Exception e) {
            log.warn("Failed to email customer {}: {}", customer.getEmail(), e.getMessage());
        }
    }

    public void createInternalAlert(User recipient, String title, String message,
                                     AlertType alertType, UUID referenceId, String referenceType) {
        try {
            Alert alert = Alert.builder()
                    .recipient(recipient)
                    .title(title)
                    .message(message)
                    .alertType(alertType)
                    .referenceId(referenceId)
                    .referenceType(referenceType)
                    .build();
            alertRepository.save(alert);
        } catch (Exception e) {
            log.warn("Failed to create internal alert for user {}: {}", recipient.getId(), e.getMessage());
        }
    }
}
