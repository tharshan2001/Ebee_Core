package com.ebee.auth.service;

import com.ebee.auth.dto.AuthResponse;
import com.ebee.auth.dto.LoginRequest;
import com.ebee.auth.dto.RegisterRequest;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.notification.NotificationService;
import com.ebee.notification.model.EmailRequest;
import com.ebee.notification.model.SmsRequest;
import com.ebee.user.dto.UserRequest;
import com.ebee.user.dto.UserResponse;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.repository.UserRepository;
import com.ebee.user.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final OtpService otpService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        String token = jwtService.generateToken(authentication);
        String refreshToken = jwtService.generateRefreshToken(authentication);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));

        sendLoginAlert(user);

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
                .build();
    }

    private void sendLoginAlert(User user) {
        if (!Boolean.TRUE.equals(user.getLoginAlertsEnabled())) {
            return;
        }

        try {
            String time = LocalDateTime.now().toString();
            String subject = "Ebee - New Login Detected";

            notificationService.sendHtmlEmail(user.getEmail(), subject, "login-alert.html",
                    Map.of("firstName", user.getFirstName(), "loginTime", time, "email", user.getEmail()));

            if (user.getPhoneNumber() != null) {
                notificationService.sendSms(SmsRequest.builder()
                        .phoneNumbers(List.of(user.getPhoneNumber()))
                        .message("Ebee: New login detected on your account at " + time)
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to send login alert to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void sendLogoutAlert(User user) {
        if (!Boolean.TRUE.equals(user.getLoginAlertsEnabled())) {
            return;
        }

        try {
            String time = LocalDateTime.now().toString();
            String subject = "Ebee - Logout Detected";

            notificationService.sendEmail(EmailRequest.builder()
                    .to(user.getEmail())
                    .subject(subject)
                    .body("You have been logged out of your Ebee account.\n\nTime: " + time
                            + "\n\nIf this was not you, please change your password immediately.")
                    .html(false)
                    .build());

            if (user.getPhoneNumber() != null) {
                notificationService.sendSms(SmsRequest.builder()
                        .phoneNumbers(List.of(user.getPhoneNumber()))
                        .message("Ebee: You have been logged out of your account at " + time)
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to send logout alert to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void sendPasswordChangedAlert(User user) {
        try {
            String time = LocalDateTime.now().toString();
            String subject = "Ebee - Password Changed";

            notificationService.sendEmail(EmailRequest.builder()
                    .to(user.getEmail())
                    .subject(subject)
                    .body("Your Ebee account password was changed.\n\nTime: " + time
                            + "\n\nIf you did not make this change, please contact support immediately.")
                    .html(false)
                    .build());

            if (user.getPhoneNumber() != null) {
                notificationService.sendSms(SmsRequest.builder()
                        .phoneNumbers(List.of(user.getPhoneNumber()))
                        .message("Ebee: Your account password was changed at " + time
                                + ". If this was not you, contact support.")
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to send password changed alert to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        UserRequest userRequest = new UserRequest();
        userRequest.setEmail(request.getEmail());
        userRequest.setPassword(request.getPassword());
        userRequest.setFirstName(request.getFirstName());
        userRequest.setLastName(request.getLastName());
        userRequest.setPhoneNumber(request.getPhoneNumber());
        userRequest.setRole(request.getRole());

        return userService.createUser(userRequest);
    }

    public AuthResponse refreshToken(String refreshToken) {
        String email = jwtService.extractEmailFromToken(refreshToken);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

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
                .expiresAt(jwtService.getExpirationDate(newToken))
                .build();
    }

    public void logout(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        sendLogoutAlert(user);
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

        sendPasswordChangedAlert(user);
    }

    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        otpService.generateAndSendOtp(user.getEmail());
    }

    public String verifyOtp(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (!otpService.verifyOtp(user.getEmail(), otp)) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }

        return generateResetToken(user);
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

        sendPasswordChangedAlert(user);
    }

    private String generateResetToken(User user) {
        return Jwts.builder()
                .setSubject(user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 300_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    private String extractEmailFromResetToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }
    }
}
