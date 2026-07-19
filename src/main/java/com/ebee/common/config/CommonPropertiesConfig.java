package com.ebee.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AppProperties.class, MinioProperties.class, PrintProperties.class})
public class CommonPropertiesConfig {
}
