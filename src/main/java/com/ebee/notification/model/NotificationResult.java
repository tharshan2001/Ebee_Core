package com.ebee.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResult {
    private boolean success;
    private String message;
    private String serverRef;
    private String channel;
}
