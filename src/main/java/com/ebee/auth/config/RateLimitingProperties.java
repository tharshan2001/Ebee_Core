package com.ebee.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "rate-limiting")
public class RateLimitingProperties {

    private final Login login;
    private final Generic generic;
    private final Ip ip;
    private final PasswordReset passwordReset;

    @ConstructorBinding
    public RateLimitingProperties(Login login, Generic generic, Ip ip, PasswordReset passwordReset) {
        this.login = login != null ? login : new Login(5, 15);
        this.generic = generic != null ? generic : new Generic(20, 15);
        this.ip = ip != null ? ip : new Ip(100, 1);
        this.passwordReset = passwordReset != null ? passwordReset : new PasswordReset(3, 20, 15);
    }

    public Login getLogin() {
        return login;
    }

    public Generic getGeneric() {
        return generic;
    }

    public Ip getIp() {
        return ip;
    }

    public PasswordReset getPasswordReset() {
        return passwordReset;
    }

    public static class Login {
        private final int maxAttempts;
        private final int windowMinutes;

        @ConstructorBinding
        public Login(Integer maxAttempts, Integer windowMinutes) {
            this.maxAttempts = maxAttempts != null ? maxAttempts : 5;
            this.windowMinutes = windowMinutes != null ? windowMinutes : 15;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public int getWindowMinutes() {
            return windowMinutes;
        }
    }

    public static class Generic {
        private final int maxRequests;
        private final int windowMinutes;

        @ConstructorBinding
        public Generic(Integer maxRequests, Integer windowMinutes) {
            this.maxRequests = maxRequests != null ? maxRequests : 20;
            this.windowMinutes = windowMinutes != null ? windowMinutes : 15;
        }

        public int getMaxRequests() {
            return maxRequests;
        }

        public int getWindowMinutes() {
            return windowMinutes;
        }
    }

    public static class Ip {
        private final int maxRequests;
        private final int windowMinutes;

        @ConstructorBinding
        public Ip(Integer maxRequests, Integer windowMinutes) {
            this.maxRequests = maxRequests != null ? maxRequests : 100;
            this.windowMinutes = windowMinutes != null ? windowMinutes : 1;
        }

        public int getMaxRequests() {
            return maxRequests;
        }

        public int getWindowMinutes() {
            return windowMinutes;
        }
    }

    /** Limits on OTP-sending endpoints, which cost money (SMS) and can be used to spam users. */
    public static class PasswordReset {
        private final int maxPerEmail;
        private final int maxPerIp;
        private final int windowMinutes;

        @ConstructorBinding
        public PasswordReset(Integer maxPerEmail, Integer maxPerIp, Integer windowMinutes) {
            this.maxPerEmail = maxPerEmail != null ? maxPerEmail : 3;
            this.maxPerIp = maxPerIp != null ? maxPerIp : 20;
            this.windowMinutes = windowMinutes != null ? windowMinutes : 15;
        }

        public int getMaxPerEmail() {
            return maxPerEmail;
        }

        public int getMaxPerIp() {
            return maxPerIp;
        }

        public int getWindowMinutes() {
            return windowMinutes;
        }
    }
}
