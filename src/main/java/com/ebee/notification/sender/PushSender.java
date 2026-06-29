package com.ebee.notification.sender;

import com.ebee.notification.model.PushRequest;

public interface PushSender {
    void send(PushRequest request);
}
