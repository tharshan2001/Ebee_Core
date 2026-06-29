package com.ebee.notification;

import com.ebee.agent.entity.Agent;
import com.ebee.alert.entity.Alert;
import com.ebee.alert.enums.AlertType;
import com.ebee.alert.repository.AlertRepository;
import com.ebee.customer.entity.Customer;
import com.ebee.notification.model.EmailRequest;
import com.ebee.notification.model.PushRequest;
import com.ebee.notification.model.SmsRequest;
import com.ebee.notification.sender.EmailSender;
import com.ebee.notification.sender.PushSender;
import com.ebee.notification.sender.SmsSender;
import com.ebee.notification.template.NotificationChannel;
import com.ebee.notification.template.NotificationTemplateService;
import com.ebee.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService implements NotificationPort {

    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final PushSender pushSender;
    private final NotificationTemplateService templateService;
    private final AlertRepository alertRepository;

    @Override
    @Async
    @Transactional(readOnly = true)
    public void sendEmail(EmailRequest request) {
        try {
            emailSender.send(request);
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", request.getTo(), e.getMessage());
        }
    }

    @Override
    @Async
    @Transactional(readOnly = true)
    public void sendSms(SmsRequest request) {
        try {
            smsSender.send(request);
        } catch (Exception e) {
            log.warn("Failed to send SMS to {}: {}", request.getPhoneNumbers(), e.getMessage());
        }
    }

    @Override
    @Async
    public void sendPush(PushRequest request) {
        try {
            pushSender.send(request);
        } catch (Exception e) {
            log.warn("Failed to send push to {}: {}", request.token(), e.getMessage());
        }
    }

    @Override
    @Async
    @Transactional(readOnly = true)
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

    @Override
    @Async
    @Transactional(readOnly = true)
    public void notifyAgent(Agent agent, String subject, String templateName, Map<String, String> placeholders, Set<String> unescapedKeys) {
        try {
            String emailBody = templateService.resolve(templateName, NotificationChannel.EMAIL, placeholders, unescapedKeys);
            if (!emailBody.isEmpty() && agent.getUser() != null && agent.getUser().getEmail() != null) {
                EmailRequest emailReq = EmailRequest.builder()
                        .to(agent.getUser().getEmail())
                        .subject(subject)
                        .body(emailBody)
                        .html(true)
                        .build();
                emailSender.send(emailReq);
            }
        } catch (Exception e) {
            log.warn("Failed to template-email agent {}: {}", agent.getAgentCode(), e.getMessage());
        }

        try {
            String smsBody = templateService.resolve(templateName, NotificationChannel.SMS, placeholders);
            if (!smsBody.isEmpty() && agent.getPhoneNumber() != null) {
                SmsRequest smsReq = SmsRequest.builder()
                        .phoneNumbers(List.of(agent.getPhoneNumber()))
                        .message(smsBody)
                        .build();
                smsSender.send(smsReq);
            }
        } catch (Exception e) {
            log.warn("Failed to template-SMS agent {}: {}", agent.getAgentCode(), e.getMessage());
        }
    }

    @Override
    @Async
    @Transactional(readOnly = true)
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

    @Override
    @Async
    @Transactional(readOnly = true)
    public void notifyUser(User user, String subject, String templateName, Map<String, String> placeholders, Set<String> unescapedKeys) {
        try {
            String emailBody = templateService.resolve(templateName, NotificationChannel.EMAIL, placeholders, unescapedKeys);
            if (!emailBody.isEmpty() && user.getEmail() != null) {
                EmailRequest emailReq = EmailRequest.builder()
                        .to(user.getEmail())
                        .subject(subject)
                        .body(emailBody)
                        .html(true)
                        .build();
                emailSender.send(emailReq);
            }
        } catch (Exception e) {
            log.warn("Failed to template-email user {}: {}", user.getEmail(), e.getMessage());
        }

        try {
            String smsBody = templateService.resolve(templateName, NotificationChannel.SMS, placeholders);
            if (!smsBody.isEmpty() && user.getPhoneNumber() != null) {
                SmsRequest smsReq = SmsRequest.builder()
                        .phoneNumbers(List.of(user.getPhoneNumber()))
                        .message(smsBody)
                        .build();
                smsSender.send(smsReq);
            }
        } catch (Exception e) {
            log.warn("Failed to template-SMS user {}: {}", user.getPhoneNumber(), e.getMessage());
        }
    }

    @Override
    @Async
    @Transactional(readOnly = true)
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

    @Override
    @Async
    @Transactional(readOnly = true)
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

    @Override
    @Async
    @Transactional(readOnly = true)
    public void notifyCustomer(Customer customer, String subject, String templateName, Map<String, String> placeholders, Set<String> unescapedKeys) {
        try {
            String smsBody = templateService.resolve(templateName, NotificationChannel.SMS, placeholders);
            if (!smsBody.isEmpty() && customer.getPhoneNumber() != null) {
                SmsRequest smsReq = SmsRequest.builder()
                        .phoneNumbers(List.of(customer.getPhoneNumber()))
                        .message(smsBody)
                        .build();
                smsSender.send(smsReq);
            }
        } catch (Exception e) {
            log.warn("Failed to template-SMS customer {}: {}", customer.getPhoneNumber(), e.getMessage());
        }

        try {
            String emailBody = templateService.resolve(templateName, NotificationChannel.EMAIL, placeholders, unescapedKeys);
            if (!emailBody.isEmpty() && customer.getEmail() != null) {
                EmailRequest emailReq = EmailRequest.builder()
                        .to(customer.getEmail())
                        .subject(subject)
                        .body(emailBody)
                        .html(true)
                        .build();
                emailSender.send(emailReq);
            }
        } catch (Exception e) {
            log.warn("Failed to template-email customer {}: {}", customer.getEmail(), e.getMessage());
        }
    }

    @Override
    @Async
    @Transactional(readOnly = true)
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
