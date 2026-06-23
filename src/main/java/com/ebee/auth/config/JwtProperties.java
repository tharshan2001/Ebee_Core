package com.ebee.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private final String secret;
    private final String resetSecret;
    private final long expiration;
    private final long refreshExpiration;

    @ConstructorBinding
    public JwtProperties(String secret, String resetSecret, Long expiration, Long refreshExpiration) {
        this.secret = secret;
        this.resetSecret = resetSecret;
        this.expiration = expiration != null ? expiration : 86_400_000L;
        this.refreshExpiration = refreshExpiration != null ? refreshExpiration : 604_800_000L;
    }

    public String getSecret() {
        return secret;
    }

    public String getResetSecret() {
        return resetSecret;
    }

    public long getExpiration() {
        return expiration;
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }
}
