package com.ebee.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "rate-limiting")
public class RateLimitingProperties {

    private final Login login;
    private final Generic generic;
    private final Ip ip;

    @ConstructorBinding
    public RateLimitingProperties(Login login, Generic generic, Ip ip) {
        this.login = login != null ? login : new Login(5, 15);
        this.generic = generic != null ? generic : new Generic(20, 15);
        this.ip = ip != null ? ip : new Ip(100, 1);
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
}
