package com.ebee.driver.config;

import com.ebee.auth.service.JwtService;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock private JwtService jwtService;
    @Mock private TopicAuthorizationRegistry authorizationRegistry;
    @Mock private UserRepository userRepository;
    @Mock private DriverRepository driverRepository;

    @InjectMocks private WebSocketAuthInterceptor interceptor;

    private UUID ownDriverId;
    private UUID ownUserId;
    private String email;

    @BeforeEach
    void setUp() {
        ownDriverId = UUID.randomUUID();
        ownUserId = UUID.randomUUID();
        email = "driver@ebee.com";
    }

    private Message<byte[]> subscribeMessage(String destination, String role) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void driverSubscribesToOwnTripOrders_allowed() {
        when(authorizationRegistry.getAllowedRoles("/topic/trip-orders/" + ownDriverId))
                .thenReturn(Optional.of(java.util.Set.of(com.ebee.user.enums.RoleType.DRIVER)));
        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(User.builder().id(ownUserId).email(email).build()));
        when(driverRepository.findByUserId(ownUserId))
                .thenReturn(Optional.of(Driver.builder().id(ownDriverId).build()));

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/trip-orders/" + ownDriverId, "DRIVER"), null);

        assertThat(result).isNotNull();
    }

    @Test
    void driverSubscribesToAnotherDriversTripOrders_denied() {
        UUID otherDriverId = UUID.randomUUID();
        when(authorizationRegistry.getAllowedRoles("/topic/trip-orders/" + otherDriverId))
                .thenReturn(Optional.of(java.util.Set.of(com.ebee.user.enums.RoleType.DRIVER)));
        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(User.builder().id(ownUserId).email(email).build()));
        when(driverRepository.findByUserId(ownUserId))
                .thenReturn(Optional.of(Driver.builder().id(ownDriverId).build()));

        assertThatThrownBy(() -> interceptor.preSend(
                subscribeMessage("/topic/trip-orders/" + otherDriverId, "DRIVER"), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void driverSubscribesToMalformedTripOrders_denied() {
        when(authorizationRegistry.getAllowedRoles("/topic/trip-orders/not-a-uuid"))
                .thenReturn(Optional.of(java.util.Set.of(com.ebee.user.enums.RoleType.DRIVER)));

        assertThatThrownBy(() -> interceptor.preSend(
                subscribeMessage("/topic/trip-orders/not-a-uuid", "DRIVER"), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void adminSubscribesToAnyTripOrders_allowed() {
        UUID anyDriverId = UUID.randomUUID();
        when(authorizationRegistry.getAllowedRoles("/topic/trip-orders/" + anyDriverId))
                .thenReturn(Optional.of(java.util.Set.of(
                        com.ebee.user.enums.RoleType.ADMIN,
                        com.ebee.user.enums.RoleType.SUPER_ADMIN,
                        com.ebee.user.enums.RoleType.DRIVER)));

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/trip-orders/" + anyDriverId, "ADMIN"), null);

        assertThat(result).isNotNull();
    }
}
