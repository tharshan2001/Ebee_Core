package com.ebee.auth.service;

import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.auth.dto.AuthResponse;
import com.ebee.auth.dto.LoginRequest;
import com.ebee.auth.dto.RegisterRequest;
import com.ebee.auth.event.PasswordChangedEvent;
import com.ebee.auth.security.UserPrincipal;
import com.ebee.auth.security.RateLimitingService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.util.PhoneUtils;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.enums.DriverAvailability;
import com.ebee.driver.repository.DriverRepository;
import org.springframework.security.access.AccessDeniedException;
import com.ebee.notification.NotificationPort;
import com.ebee.user.dto.UserRequest;
import com.ebee.user.dto.UserResponse;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.enums.UserStatus;
import com.ebee.user.repository.UserRepository;
import com.ebee.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationPort notificationPort;
    private final OtpService otpService;
    private final DriverRepository driverRepository;
    private final AuditLogService auditLogService;
    private final RateLimitingService rateLimitingService;
    private final IpWhitelistService ipWhitelistService;
    private final ApplicationEventPublisher eventPublisher;

    public AuthResponse login(LoginRequest request, String clientIp) {
        String email = request.getEmail();
        String loginKey = "login:" + email;

        if (!rateLimitingService.isLoginAllowed(loginKey)) {
            log.warn("Login rate limit exceeded for: {}", email);
            throw new IllegalStateException("Too many login attempts. Please try again later.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        rateLimitingService.resetLoginAttempts(loginKey);

        User user = ((UserPrincipal) authentication.getPrincipal()).getUser();

        if (request.getRole() != null && user.getRole() != request.getRole()) {
            throw new AccessDeniedException("Invalid role for login: expected " + request.getRole()
                    + " but user has role " + user.getRole());
        }

        java.util.Optional<Driver> driverOpt = null;
        if (user.getRole() == RoleType.DRIVER) {
            driverOpt = driverRepository.findByUserId(user.getId());
            driverOpt.filter(d -> d.getAvailability() == DriverAvailability.SUSPENDED)
                    .ifPresent(d -> { throw new IllegalStateException("Your account has been suspended"); });
        }

        if (user.getRole() == RoleType.ADMIN || user.getRole() == RoleType.SUPER_ADMIN) {
            if (!ipWhitelistService.isAllowed(user, clientIp)) {
                log.warn("Login blocked by IP whitelist: user={} role={} ip={}", email, user.getRole(), clientIp);
                throw new AccessDeniedException("Access denied.");
            }
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtService.generateToken(authentication);
        String refreshToken = jwtService.generateRefreshToken(authentication);

        sendLoginAlert(user);

        auditLogService.record(AuditAction.LOGIN, "User", user.getId(),
                "User " + user.getEmail() + " logged in as " + user.getRole());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .mustChangePassword(user.getMustChangePassword())
                .expiresAt(jwtService.getExpirationDate(token))
                .driverId(driverOpt != null ? driverOpt.map(Driver::getId).orElse(null) : null)
                .licenseNumber(driverOpt != null ? driverOpt.map(Driver::getLicenseNumber).orElse(null) : null)
                .driverAvailability(driverOpt != null ? driverOpt.map(d -> d.getAvailability().name()).orElse(null) : null)
                .build();
    }

    private void sendLoginAlert(User user) {
        if (!Boolean.TRUE.equals(user.getLoginAlertsEnabled())) {
            return;
        }

        try {
            String time = LocalDateTime.now().toString();
            String subject = "Ebee - New Login Detected";
            notificationPort.notifyUser(user, subject, "login-alert",
                    Map.of("firstName", user.getFirstName(), "loginTime", time, "email", user.getEmail()),
                    Set.of());
        } catch (Exception e) {
            log.warn("Failed to send login alert to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    private void sendLogoutAlert(User user) {
        if (!Boolean.TRUE.equals(user.getLoginAlertsEnabled())) {
            return;
        }

        try {
            String time = LocalDateTime.now().toString();
            String subject = "Ebee - Logout Detected";
            notificationPort.notifyUser(user, subject, "logout-alert",
                    Map.of("firstName", user.getFirstName(), "logoutTime", time, "email", user.getEmail()),
                    Set.of());
        } catch (Exception e) {
            log.warn("Failed to send logout alert to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    private void sendPasswordChangedAlert(User user) {
        try {
            String time = LocalDateTime.now().toString();
            String subject = "Ebee - Password Changed";
            notificationPort.notifyUser(user, subject, "password-changed",
                    Map.of("firstName", user.getFirstName(), "changeTime", time, "email", user.getEmail()),
                    Set.of());
        } catch (Exception e) {
            log.error("Failed to send password changed alert to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        UserRequest userRequest = new UserRequest();
        userRequest.setEmail(request.getEmail());
        userRequest.setPassword(request.getPassword());
        userRequest.setFirstName(request.getFirstName());
        userRequest.setLastName(request.getLastName());
        userRequest.setPhoneNumber(PhoneUtils.normalize(request.getPhoneNumber()));
        userRequest.setRole(request.getRole());

        return userService.createUser(userRequest);
    }

    public AuthResponse refreshToken(String refreshToken) {
        String email = jwtService.extractEmailFromToken(refreshToken);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (!Boolean.TRUE.equals(user.getIsActive()) || user.getStatus() != UserStatus.ACTIVE) {
            throw new DisabledException("User account is not active");
        }

        String refreshTokenHash = sha256Hex(refreshToken);
        if (!refreshTokenHash.equals(user.getRefreshTokenHash())) {
            throw new AccessDeniedException("Invalid refresh token");
        }

        Collection<? extends GrantedAuthority> authorities =
            java.util.Collections.singletonList(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole().name())
            );

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, authorities
        );

        String newToken = jwtService.generateToken(authentication);
        String newRefreshToken = jwtService.generateRefreshToken(authentication);

        user.setRefreshTokenHash(sha256Hex(newRefreshToken));
        userRepository.save(user);

        return AuthResponse.builder()
                .token(newToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .mustChangePassword(user.getMustChangePassword())
                .expiresAt(jwtService.getExpirationDate(newToken))
                .driverId(driverId(user))
                .licenseNumber(licenseNumber(user))
                .driverAvailability(driverAvailability(user))
                .build();
    }

    private UUID driverId(User user) {
        return user.getRole() == RoleType.DRIVER
                ? driverRepository.findByUserId(user.getId()).map(Driver::getId).orElse(null)
                : null;
    }

    private String licenseNumber(User user) {
        return user.getRole() == RoleType.DRIVER
                ? driverRepository.findByUserId(user.getId()).map(Driver::getLicenseNumber).orElse(null)
                : null;
    }

    private String driverAvailability(User user) {
        return user.getRole() == RoleType.DRIVER
                ? driverRepository.findByUserId(user.getId()).map(d -> d.getAvailability().name()).orElse(null)
                : null;
    }

    public void logout(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        sendLogoutAlert(user);
        auditLogService.record(AuditAction.LOGOUT, "User", user.getId(),
                "User " + user.getEmail() + " logged out");
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        auditLogService.record(AuditAction.PASSWORD_CHANGE, "User", user.getId(),
                "User " + user.getEmail() + " changed their password");
        eventPublisher.publishEvent(new PasswordChangedEvent(this, user));
    }

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            otpService.generateAndSendOtp(user.getEmail());
            auditLogService.record(AuditAction.FORGOT_PASSWORD, "User", user.getId(),
                    "User " + user.getEmail() + " requested password reset");
        });
    }

    public String verifyOtp(String email, String otp) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty() || !otpService.verifyOtp(email, otp)) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }

        return generateResetToken(userOpt.get());
    }

    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        String email = extractEmailFromResetToken(resetToken);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        auditLogService.record(AuditAction.PASSWORD_RESET, "User", user.getId(),
                "Password reset completed for " + user.getEmail());
        eventPublisher.publishEvent(new PasswordChangedEvent(this, user));
    }

    private String generateResetToken(User user) {
        return jwtService.generateResetToken(user.getEmail());
    }

    private String extractEmailFromResetToken(String token) {
        return jwtService.extractEmailFromResetToken(token);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
