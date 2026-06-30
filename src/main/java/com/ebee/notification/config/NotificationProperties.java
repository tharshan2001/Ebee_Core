package com.ebee.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private Sms sms = new Sms();

    @Data
    public static class Sms {
        private String apiBaseUrl;
        private String username;
        private String password;
        private String authString;
        private String defaultMask;
        private String defaultCampaign;
    }
}
