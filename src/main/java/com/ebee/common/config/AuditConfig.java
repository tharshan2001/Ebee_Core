package com.ebee.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
// @EnableAspectJAutoProxy is auto-configured by spring-boot-starter-aop; retained only for explicitness
@EnableAspectJAutoProxy
public class AuditConfig {
}
