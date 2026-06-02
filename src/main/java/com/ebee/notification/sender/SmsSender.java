package com.ebee.notification.sender;

import com.ebee.notification.model.NotificationResult;
import com.ebee.notification.model.SmsRequest;

public interface SmsSender {
    NotificationResult send(SmsRequest request);
}
