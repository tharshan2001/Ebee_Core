package com.ebee.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Backup backup;
    private final Cors cors;
    private final IpWhitelist ipWhitelist;
    private final Driver driver;

    @ConstructorBinding
    public AppProperties(Backup backup, Cors cors, IpWhitelist ipWhitelist, Driver driver) {
        this.backup = backup != null ? backup : new Backup();
        this.cors = cors != null ? cors : new Cors();
        this.ipWhitelist = ipWhitelist != null ? ipWhitelist : new IpWhitelist();
        this.driver = driver != null ? driver : new Driver();
    }

    public Backup getBackup() {
        return backup;
    }

    public Cors getCors() {
        return cors;
    }

    public IpWhitelist getIpWhitelist() {
        return ipWhitelist;
    }

    public Driver getDriver() {
        return driver;
    }

    public static class Backup {
        private final String dir;

        @ConstructorBinding
        public Backup(String dir) {
            this.dir = dir;
        }

        public Backup() {
            this.dir = null;
        }

        public String getDir() {
            return dir;
        }
    }

    public static class Cors {
        private final String allowedOrigins;

        @ConstructorBinding
        public Cors(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public Cors() {
            this.allowedOrigins = "";
        }

        public String getAllowedOrigins() {
            return allowedOrigins;
        }
    }

    public static class IpWhitelist {
        private final String superAdminIps;

        @ConstructorBinding
        public IpWhitelist(String superAdminIps) {
            this.superAdminIps = superAdminIps;
        }

        public IpWhitelist() {
            this.superAdminIps = "";
        }

        public String getSuperAdminIps() {
            return superAdminIps;
        }
    }

    public static class Driver {
        private final Long gpsIntervalMs;
        private final Integer minAccuracy;

        @ConstructorBinding
        public Driver(Long gpsIntervalMs, Integer minAccuracy) {
            this.gpsIntervalMs = gpsIntervalMs != null ? gpsIntervalMs : 1000L;
            this.minAccuracy = minAccuracy != null ? minAccuracy : 10;
        }

        public Driver() {
            this.gpsIntervalMs = 1000L;
            this.minAccuracy = 10;
        }

        public Long getGpsIntervalMs() {
            return gpsIntervalMs;
        }

        public Integer getMinAccuracy() {
            return minAccuracy;
        }
    }
}
