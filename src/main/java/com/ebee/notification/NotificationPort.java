package com.ebee.notification;

import com.ebee.agent.entity.Agent;
import com.ebee.alert.enums.AlertType;
import com.ebee.customer.entity.Customer;
import com.ebee.notification.model.EmailRequest;
import com.ebee.notification.model.SmsRequest;
import com.ebee.user.entity.User;

import java.util.Map;
import java.util.UUID;

public interface NotificationPort {

    void sendEmail(EmailRequest request);

    void sendSms(SmsRequest request);

    void notifyAgent(Agent agent, String subject, String message);

    void notifyUser(User user, String subject, String message);

    void sendHtmlEmail(String to, String subject, String template, Map<String, String> placeholders);

    void notifyCustomer(Customer customer, String subject, String message);

    void createInternalAlert(User recipient, String title, String message,
                             AlertType alertType, UUID referenceId, String referenceType);
}
