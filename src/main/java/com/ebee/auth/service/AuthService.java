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
import com.ebee.notification.model.EmailRequest;
import com.ebee.notification.model.SmsRequest;
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

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final ApplicationEventPublisher eventPublisher;

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail();
        String loginKey = "login:" + email;

        if (!rateLimitingService.isLoginAllowed(loginKey)) {
            log.warn("Login rate limit exceeded for: {}", email);
            throw new IllegalStateException("Too many login attempts. Please try again later.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (Exception e) {
            log.debug("Authentication failed for: {}", email);
            throw e;
        }

        rateLimitingService.resetLoginAttempts(loginKey);

        User user = ((UserPrincipal) authentication.getPrincipal()).getUser();

        if (request.getRole() != null && user.getRole() != request.getRole()) {
            throw new AccessDeniedException("Invalid role for login: expected " + request.getRole()
                    + " but user has role " + user.getRole());
        }

        if (user.getRole() == RoleType.DRIVER) {
            driverRepository.findByUserId(user.getId())
                    .filter(d -> d.getAvailability() == DriverAvailability.SUSPENDED)
                    .ifPresent(d -> { throw new IllegalStateException("Your account has been suspended"); });
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
                .driverId(driverId(user))
                .licenseNumber(licenseNumber(user))
                .driverAvailability(driverAvailability(user))
                .build();
    }

    private void sendLoginAlert(User user) {
        if (!Boolean.TRUE.equals(user.getLoginAlertsEnabled())) {
            return;
        }

        try {
            String time = LocalDateTime.now().toString();
            String subject = "Ebee - New Login Detected";

            notificationPort.sendHtmlEmail(user.getEmail(), subject, "login-alert.html",
                    Map.of("firstName", user.getFirstName(), "loginTime", time, "email", user.getEmail()));

            if (user.getPhoneNumber() != null) {
                notificationPort.sendSms(SmsRequest.builder()
                        .phoneNumbers(List.of(user.getPhoneNumber()))
                        .message("Ebee: New login detected on your account at " + time)
                        .build());
            }
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

            notificationPort.sendEmail(EmailRequest.builder()
                    .to(user.getEmail())
                    .subject(subject)
                    .body("You have been logged out of your Ebee account.\n\nTime: " + time
                            + "\n\nIf this was not you, please change your password immediately.")
                    .html(false)
                    .build());

            if (user.getPhoneNumber() != null) {
                notificationPort.sendSms(SmsRequest.builder()
                        .phoneNumbers(List.of(user.getPhoneNumber()))
                        .message("Ebee: You have been logged out of your account at " + time)
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to send logout alert to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    private void sendPasswordChangedAlert(User user) {
        try {
            String time = LocalDateTime.now().toString();
            String subject = "Ebee - Password Changed";

            notificationPort.sendEmail(EmailRequest.builder()
                    .to(user.getEmail())
                    .subject(subject)
                    .body("Your Ebee account password was changed.\n\nTime: " + time
                            + "\n\nIf you did not make this change, please contact support immediately.")
                    .html(false)
                    .build());

            if (user.getPhoneNumber() != null) {
                notificationPort.sendSms(SmsRequest.builder()
                        .phoneNumbers(List.of(user.getPhoneNumber()))
                        .message("Ebee: Your account password was changed at " + time
                                + ". If this was not you, contact support.")
                        .build());
            }
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

        Collection<? extends GrantedAuthority> authorities =
            java.util.Collections.singletonList(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole().name())
            );

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, authorities
        );

        String newToken = jwtService.generateToken(authentication);
        String newRefreshToken = jwtService.generateRefreshToken(authentication);

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
}
