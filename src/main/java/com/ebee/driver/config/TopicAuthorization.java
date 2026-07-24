package com.ebee.driver.config;

import com.ebee.user.enums.RoleType;

import java.util.Set;

/**
 * Declares a single STOMP topic prefix and the roles permitted to subscribe to it.
 * Used by {@link TopicAuthorizationRegistry} to enforce role-based access on
 * {@code /topic/*} destinations without hardcoding mappings inside the
 * {@link WebSocketAuthInterceptor}.
 *
 * <p>Adding a new broadcast topic requires only registering a new instance here —
 * the interceptor delegates all decisions to the registry (Open/Closed Principle).
 *
 * @param prefix       the STOMP destination prefix (e.g. {@code "/topic/orders"})
 * @param allowedRoles roles that may subscribe; all others are rejected
 */
public record TopicAuthorization(String prefix, Set<RoleType> allowedRoles) {
}
