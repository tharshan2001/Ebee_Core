package com.ebee.driver.config;

import com.ebee.user.enums.RoleType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Central registry of STOMP topic subscriptions and their required roles.
 * The {@link WebSocketAuthInterceptor} queries this registry on every
 * {@code SUBSCRIBE} frame to decide whether the caller's role grants access.
 *
 * <p>Topic authorization is intentionally separated from JWT authentication
 * (handled by the interceptor) so that adding or changing a topic's access
 * policy never requires modifying the interceptor itself. New broadcast topics
 * are added by appending a {@link TopicAuthorization} entry to
 * {@link #authorizations}.
 *
 * <p>If a destination does not match any registered prefix the subscription is
 * <em>allowed</em> (open by default). This keeps non-feed topics — such as
 * user-specific queues — unblocked.
 */
@Component
public class TopicAuthorizationRegistry {

    private final List<TopicAuthorization> authorizations = List.of(
            new TopicAuthorization("/topic/deliveries",
                    Set.of(RoleType.ADMIN, RoleType.SUPER_ADMIN, RoleType.AGENT, RoleType.DRIVER)),
            new TopicAuthorization("/topic/orders",
                    Set.of(RoleType.ADMIN, RoleType.SUPER_ADMIN)),
            new TopicAuthorization("/topic/driver-locations",
                    Set.of(RoleType.ADMIN, RoleType.SUPER_ADMIN)),
            new TopicAuthorization("/topic/customer-dues",
                    Set.of(RoleType.ADMIN, RoleType.SUPER_ADMIN)),
            new TopicAuthorization("/topic/supplier-dues",
                    Set.of(RoleType.ADMIN, RoleType.SUPER_ADMIN)),
            new TopicAuthorization("/topic/trips",
                    Set.of(RoleType.ADMIN, RoleType.SUPER_ADMIN, RoleType.DRIVER)),
            new TopicAuthorization("/topic/trip-orders",
                    Set.of(RoleType.ADMIN, RoleType.SUPER_ADMIN, RoleType.DRIVER))
    );

    /**
     * Return the set of roles allowed to subscribe to the given destination,
     * or {@link Optional#empty()} if the destination is not governed by any
     * registered prefix (unrestricted).
     */
    public Optional<Set<RoleType>> getAllowedRoles(String destination) {
        return authorizations.stream()
                .filter(a -> destination.startsWith(a.prefix()))
                .map(TopicAuthorization::allowedRoles)
                .findFirst();
    }
}
