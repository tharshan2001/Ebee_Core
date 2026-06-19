package com.ebee.seed;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.super-admin")
public class SuperAdminProperties {

    private String email;
    private String password;
    private String firstName;
    private String lastName;
}
