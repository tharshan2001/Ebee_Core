package com.ebee.auth.service;

import com.ebee.auth.dto.LoginRequest;
import com.ebee.auth.dto.RegisterRequest;
import com.ebee.auth.security.RateLimitingService;
import com.ebee.auth.security.UserPrincipal;
import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.enums.DriverAvailability;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.notification.NotificationPort;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.enums.UserStatus;
import com.ebee.user.repository.UserRepository;
import com.ebee.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationPort notificationPort;
    @Mock private OtpService otpService;
    @Mock private DriverRepository driverRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private RateLimitingService rateLimitingService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private AuthService authService;

    private User createUser(String email, RoleType role, boolean mustChangePassword) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password("encoded-password")
                .firstName("Test")
                .lastName("User")
                .role(role)
                .status(UserStatus.ACTIVE)
                .isActive(true)
                .mustChangePassword(mustChangePassword)
                .phoneNumber("0771234567")
                .loginAlertsEnabled(false)
                .build();
    }

    @Test
    void login_withValidCredentials_returnsAuthResponse() {
        String email = "admin@ebee.com";
        String password = "password123";
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setRole(RoleType.ADMIN);

        User user = createUser(email, RoleType.ADMIN, false);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new UserPrincipal(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(rateLimitingService.isLoginAllowed(anyString())).thenReturn(true);
        when(jwtService.generateToken(any(Authentication.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(Authentication.class))).thenReturn("refresh-token");

        var response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getEmail()).isEqualTo(email);
        assertThat(response.getRole()).isEqualTo(RoleType.ADMIN);

        verify(rateLimitingService).resetLoginAttempts("login:" + email);
        verify(auditLogService).record(eq(AuditAction.LOGIN), eq("User"), eq(user.getId()), anyString());
    }

    @Test
    void login_withWrongRole_throwsAccessDenied() {
        LoginRequest request = new LoginRequest();
        request.setEmail("agent@ebee.com");
        request.setPassword("pass");
        request.setRole(RoleType.ADMIN);

        User user = createUser("agent@ebee.com", RoleType.AGENT, false);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new UserPrincipal(user));
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(rateLimitingService.isLoginAllowed(anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Invalid role for login");
    }

    @Test
    void login_withBadCredentials_throws() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@ebee.com");
        request.setPassword("wrong");

        when(rateLimitingService.isLoginAllowed(anyString())).thenReturn(true);
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_rateLimited_throwsIllegalState() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@ebee.com");
        request.setPassword("wrong");

        when(rateLimitingService.isLoginAllowed(anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Too many login attempts");
    }

    @Test
    void login_rateLimited_doesNotAttemptAuthentication() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@ebee.com");
        request.setPassword("correct-password");

        when(rateLimitingService.isLoginAllowed(anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Too many login attempts");

        verify(authenticationManager, never()).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(rateLimitingService, never()).resetLoginAttempts(anyString());
    }

    @Test
    void login_suspendedDriver_throws() {
        LoginRequest request = new LoginRequest();
        request.setEmail("driver@ebee.com");
        request.setPassword("pass");

        User user = createUser("driver@ebee.com", RoleType.DRIVER, false);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new UserPrincipal(user));
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(rateLimitingService.isLoginAllowed(anyString())).thenReturn(true);

        Driver driver = Driver.builder()
                .id(UUID.randomUUID())
                .user(user)
                .availability(DriverAvailability.SUSPENDED)
                .build();
        when(driverRepository.findByUserId(user.getId())).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    void login_mustChangePassword_flagPreserved() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@ebee.com");
        request.setPassword("pass");
        request.setRole(RoleType.ADMIN);

        User user = createUser("admin@ebee.com", RoleType.ADMIN, true);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new UserPrincipal(user));
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(rateLimitingService.isLoginAllowed(anyString())).thenReturn(true);
        when(jwtService.generateToken(any())).thenReturn("token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh");

        var response = authService.login(request);

        assertThat(response.getMustChangePassword()).isTrue();
    }

    @Test
    void register_createsUser() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@ebee.com");
        request.setPassword("Pass123!");
        request.setFirstName("New");
        request.setLastName("User");
        request.setPhoneNumber("0771234567");
        request.setRole(RoleType.AGENT);

        authService.register(request);

        verify(userService).createUser(any());
    }

    @Test
    void logout_recordsAudit() {
        String email = "admin@ebee.com";
        User user = createUser(email, RoleType.ADMIN, false);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        authService.logout(email);

        verify(auditLogService).record(eq(AuditAction.LOGOUT), eq("User"), eq(user.getId()), anyString());
    }

    @Test
    void changePassword_withCorrectPassword_updates() {
        String email = "admin@ebee.com";
        String current = "oldPass123!";
        String newPass = "newPass456!";

        User user = createUser(email, RoleType.ADMIN, true);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(current, user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode(newPass)).thenReturn("encoded-new");

        authService.changePassword(email, current, newPass);

        assertThat(user.getMustChangePassword()).isFalse();
        verify(userRepository).save(user);
        verify(auditLogService).record(eq(AuditAction.PASSWORD_CHANGE), eq("User"), eq(user.getId()), anyString());
        verify(eventPublisher).publishEvent(any(com.ebee.auth.event.PasswordChangedEvent.class));
    }

    @Test
    void changePassword_withWrongPassword_throws() {
        String email = "admin@ebee.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(createUser(email, RoleType.ADMIN, false)));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(email, "wrong", "new"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    void refreshToken_withActiveUser_returnsNewTokens() {
        String email = "admin@ebee.com";
        String refreshToken = "valid-refresh-token";
        User user = createUser(email, RoleType.ADMIN, false);
        when(jwtService.extractEmailFromToken(refreshToken)).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(Authentication.class))).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(any(Authentication.class))).thenReturn("new-refresh-token");

        var response = authService.refreshToken(refreshToken);

        assertThat(response.getToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.getEmail()).isEqualTo(email);
    }

    @Test
    void refreshToken_withInactiveUser_throwsDisabledException() {
        String email = "admin@ebee.com";
        String refreshToken = "valid-refresh-token";
        User user = createUser(email, RoleType.ADMIN, false);
        user.setStatus(UserStatus.INACTIVE);
        when(jwtService.extractEmailFromToken(refreshToken)).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refreshToken(refreshToken))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("User account is not active");
    }

    @Test
    void refreshToken_withLockedUser_throwsDisabledException() {
        String email = "admin@ebee.com";
        String refreshToken = "valid-refresh-token";
        User user = createUser(email, RoleType.ADMIN, false);
        user.setStatus(UserStatus.LOCKED);
        when(jwtService.extractEmailFromToken(refreshToken)).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refreshToken(refreshToken))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("User account is not active");
    }

    @Test
    void refreshToken_withIsActiveFalse_throwsDisabledException() {
        String email = "admin@ebee.com";
        String refreshToken = "valid-refresh-token";
        User user = createUser(email, RoleType.ADMIN, false);
        user.setIsActive(false);
        when(jwtService.extractEmailFromToken(refreshToken)).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refreshToken(refreshToken))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("User account is not active");
    }
}
