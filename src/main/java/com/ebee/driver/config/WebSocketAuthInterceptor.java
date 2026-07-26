package com.ebee.driver.config;

import com.ebee.auth.service.JwtService;
import com.ebee.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Intercepts inbound STOMP frames on the {@code /ws} endpoint and enforces
 * two distinct security checks:
 *
 * <ol>
 *   <li><b>CONNECT</b> &mdash; validates the {@code Authorization: Bearer …}
 *       header against {@link JwtService} and sets the authenticated principal
 *       (email + role) on the STOMP session.</li>
 *   <li><b>SUBSCRIBE</b> &mdash; consults {@link TopicAuthorizationRegistry}
 *       to verify the principal's role is permitted for the requested
 *       {@code /topic/*} destination. Unregistered destinations are allowed
 *       through (open by default for user-specific queues).</li>
 * </ol>
 *
 * <p>This interceptor does <em>not</em> handle SEND or DISCONNECT frames.
 * The JWT is validated once on CONNECT; subsequent frames rely on the
 * session principal set during that handshake.
 */
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
            authorizeSubscribe(accessor);
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            authorizeSend(accessor);
        }

        return message;
    }

    /**
     * Validate the JWT bearer token on STOMP CONNECT and set the authenticated
     * principal on the session. The principal's email and role are later read
     * by {@link #authorize(StompHeaderAccessor)} during SUBSCRIBE frames.
     *
     * @throws MessageDeliveryException if the header is missing, the token is
     *         invalid/expired, or the JWT carries no role claim
     */
    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("WebSocket CONNECT rejected: missing or invalid Authorization header");
            throw new MessageDeliveryException((Message<?>) null, "Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        try {
            String email = jwtService.extractEmail(token);
            RoleType role = jwtService.extractRole(token);
            if (role == null) {
                throw new MessageDeliveryException((Message<?>) null, "JWT has no role claim");
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    email, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
            );
            accessor.setUser(authentication);
            log.info("WebSocket CONNECT OK: email={} role={}", email, role);
        } catch (MessageDeliveryException e) {
            throw e;
        } catch (Exception e) {
            log.warn("WebSocket CONNECT rejected: {}", e.getMessage());
            throw new MessageDeliveryException((Message<?>) null, "Invalid JWT: " + e.getMessage(), e);
        }
    }

    /**
     * Enforce role-based authorization for STOMP SUBSCRIBE frames. If the
     * destination matches a registered topic prefix in
     * {@link TopicAuthorizationRegistry}, the subscriber's role must be in the
     * allowed set. Destinations that do not match any prefix pass through
     * without restriction.
     *
     * @throws MessageDeliveryException if the subscriber is unauthenticated or
     *         lacks the required role
     */
    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) return;

        authorizationRegistry.getAllowedRoles(destination).ifPresent(allowedRoles -> {
            if (accessor.getUser() == null) {
                throw new MessageDeliveryException((Message<?>) null, "Authentication required for feed subscription");
            }
            RoleType role = extractRole(accessor);
            if (role == null || !allowedRoles.contains(role)) {
                throw new MessageDeliveryException((Message<?>) null, "Insufficient privileges to subscribe to " + destination);
            }
            log.info("WebSocket SUBSCRIBE OK: destination={} role={}", destination, role);
        });
    }

    /**
     * Enforce role-based authorization for STOMP SEND frames. Only drivers
     * may send to {@code /app/driver-locations/*} destinations. Unregistered
     * destinations pass through without restriction.
     *
     * @throws MessageDeliveryException if the sender is unauthenticated or
     *         lacks the required role
     */
    private void authorizeSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) return;

        if (destination.equals("/app/driver-locations/update")) {
            if (accessor.getUser() == null) {
                throw new MessageDeliveryException((Message<?>) null, "Authentication required for driver location updates");
            }
            RoleType role = extractRole(accessor);
            if (role == null || role != RoleType.DRIVER) {
                throw new MessageDeliveryException((Message<?>) null, "Only drivers may send location updates via STOMP");
            }
        }
    }

    /**
     * Extract the {@link RoleType} from the STOMP session's authentication
     * principal. The role was stored as a granted authority during CONNECT
     * ({@code ROLE_ADMIN}, {@code ROLE_DRIVER}, etc.).
     */
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
