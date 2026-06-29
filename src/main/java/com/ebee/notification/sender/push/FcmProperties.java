package com.ebee.notification.sender.push;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.push")
public class FcmProperties {
    private boolean enabled;
    private String projectId;
    private String configPath;
}
