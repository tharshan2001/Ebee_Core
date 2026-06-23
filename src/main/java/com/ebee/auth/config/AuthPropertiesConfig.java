package com.ebee.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, RateLimitingProperties.class})
public class AuthPropertiesConfig {
}
