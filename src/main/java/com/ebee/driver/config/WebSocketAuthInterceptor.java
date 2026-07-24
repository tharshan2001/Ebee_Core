package com.ebee.driver.config;

import com.ebee.auth.service.JwtService;
import com.ebee.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final TopicAuthorizationRegistry authorizationRegistry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorize(accessor);
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("WebSocket CONNECT rejected: missing or invalid Authorization header");
            throw new IllegalArgumentException("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        try {
            String email = jwtService.extractEmail(token);
            RoleType role = jwtService.extractRole(token);
            if (role == null) {
                throw new IllegalArgumentException("JWT has no role claim");
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    email, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
            );
            accessor.setUser(authentication);
            log.info("WebSocket CONNECT OK: email={} role={}", email, role);
        } catch (Exception e) {
            log.warn("WebSocket CONNECT rejected: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JWT: " + e.getMessage(), e);
        }
    }

    private void authorize(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) return;

        authorizationRegistry.getAllowedRoles(destination).ifPresent(allowedRoles -> {
            if (accessor.getUser() == null) {
                throw new IllegalArgumentException("Authentication required for feed subscription");
            }
            RoleType role = extractRole(accessor);
            if (role == null || !allowedRoles.contains(role)) {
                throw new IllegalArgumentException("Insufficient privileges to subscribe to " + destination);
            }
            log.info("WebSocket SUBSCRIBE OK: destination={} role={}", destination, role);
        });
    }

    private RoleType extractRole(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            return auth.getAuthorities().stream()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .findFirst()
                    .map(s -> {
                        try { return RoleType.valueOf(s); }
                        catch (IllegalArgumentException e) { return null; }
                    })
                    .orElse(null);
        }
        return null;
    }
}
