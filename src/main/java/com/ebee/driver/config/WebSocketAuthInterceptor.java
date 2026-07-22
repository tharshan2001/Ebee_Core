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

/**
 * Validates JWT on STOMP CONNECT frames. Rejects anonymous subscriptions and
 * limits {@code /topic/deliveries} to ADMIN / SUPER_ADMIN / AGENT roles.
 * <p>
 * Other STOMP commands (SUBSCRIBE, SEND, DISCONNECT) are passed through — the
 * {@code Authorization} header is only required on CONNECT, after which the
 * principal is set on the message and the security context is populated by
 * the inbound channel.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String DELIVERY_TOPIC = "/topic/deliveries";
    private static final Set<RoleType> DELIVERY_FEED_ROLES = Set.of(
            RoleType.ADMIN, RoleType.SUPER_ADMIN, RoleType.AGENT);

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
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
            } catch (Exception e) {
                log.warn("WebSocket CONNECT rejected: {}", e.getMessage());
                throw new IllegalArgumentException("Invalid JWT: " + e.getMessage(), e);
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith(DELIVERY_TOPIC)) {
                if (accessor.getUser() == null) {
                    throw new IllegalArgumentException("Authentication required for delivery feed subscription");
                }
                String name = accessor.getUser().getName();
                RoleType role = null;
                if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
                    Object principal = auth.getPrincipal();
                    String roleStr = principal != null ? principal.toString() : "";
                    try {
                        role = RoleType.valueOf(roleStr.replace("ROLE_", ""));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (role == null) {
                    role = jwtService.extractRole(name);
                }
                if (role == null || !DELIVERY_FEED_ROLES.contains(role)) {
                    throw new IllegalArgumentException("Insufficient privileges to subscribe to " + destination);
                }
            }
        }

        return message;
    }
}
