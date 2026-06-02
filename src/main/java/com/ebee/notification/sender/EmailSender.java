package com.ebee.notification.sender;

import com.ebee.notification.model.EmailRequest;
import com.ebee.notification.model.NotificationResult;

public interface EmailSender {
    NotificationResult send(EmailRequest request);
}
