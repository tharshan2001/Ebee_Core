package com.ebee.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.delivery")
public class DeliveryProperties {

    private final Timeout timeout;

    @ConstructorBinding
    public DeliveryProperties(Timeout timeout) {
        this.timeout = timeout != null ? timeout : new Timeout();
    }

    public Timeout getTimeout() {
        return timeout;
    }

    public static class Timeout {
        private final Duration pickup;
        private final Duration inTransit;

        @ConstructorBinding
        public Timeout(Duration pickup, Duration inTransit) {
            this.pickup = pickup != null ? pickup : Duration.ofHours(2);
            this.inTransit = inTransit != null ? inTransit : Duration.ofHours(24);
        }

        public Timeout() {
            this.pickup = Duration.ofHours(2);
            this.inTransit = Duration.ofHours(24);
        }

        public Duration getPickup() {
            return pickup;
        }

        public Duration getInTransit() {
            return inTransit;
        }
    }
}
