package com.ebee.driver.config;

import com.ebee.user.enums.RoleType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class TopicAuthorizationRegistry {

    private final List<TopicAuthorization> authorizations = List.of(
            new TopicAuthorization("/topic/deliveries",
                    Set.of(RoleType.ADMIN, RoleType.SUPER_ADMIN, RoleType.AGENT, RoleType.DRIVER)),
            new TopicAuthorization("/topic/orders",
                    Set.of(RoleType.ADMIN, RoleType.SUPER_ADMIN))
    );

    public Optional<Set<RoleType>> getAllowedRoles(String destination) {
        return authorizations.stream()
                .filter(a -> destination.startsWith(a.prefix()))
                .map(TopicAuthorization::allowedRoles)
                .findFirst();
    }
}
