package com.ebee.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Backup backup;
    private final Cors cors;
    private final IpWhitelist ipWhitelist;

    @ConstructorBinding
    public AppProperties(Backup backup, Cors cors, IpWhitelist ipWhitelist) {
        this.backup = backup != null ? backup : new Backup();
        this.cors = cors != null ? cors : new Cors();
        this.ipWhitelist = ipWhitelist != null ? ipWhitelist : new IpWhitelist();
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
            this.allowedOrigins = "http://localhost:5173,http://localhost:5174,http://localhost:8085";
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
}
