package com.ebee.driver.config;

import com.ebee.user.enums.RoleType;

import java.util.Set;

public record TopicAuthorization(String prefix, Set<RoleType> allowedRoles) {
}
