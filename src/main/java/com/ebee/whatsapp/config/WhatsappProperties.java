package com.ebee.whatsapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "whatsapp")
public class WhatsappProperties {

    private final String apiBaseUrl;
    private final String phoneNumberId;
    private final String accessToken;
    private final String verifyToken;
    private final String appSecret;

    @ConstructorBinding
    public WhatsappProperties(String apiBaseUrl, String phoneNumberId, String accessToken, String verifyToken,
                              String appSecret) {
        this.apiBaseUrl = apiBaseUrl;
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
        this.verifyToken = verifyToken;
        this.appSecret = appSecret;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getVerifyToken() {
        return verifyToken;
    }

    /** Meta app secret used to verify the X-Hub-Signature-256 header on incoming webhooks. */
    public String getAppSecret() {
        return appSecret;
    }
}
