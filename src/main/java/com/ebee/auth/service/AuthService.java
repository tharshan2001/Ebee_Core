package com.ebee.auth.service;

import com.ebee.auth.dto.AuthResponse;
import com.ebee.auth.dto.LoginRequest;
import com.ebee.auth.dto.RegisterRequest;
import com.ebee.user.dto.UserResponse;

public interface AuthService {

    AuthResponse login(LoginRequest request, String clientIp);

    UserResponse register(RegisterRequest request);

    AuthResponse refreshToken(String refreshToken);

    void logout(String email);

    void changePassword(String email, String currentPassword, String newPassword);

    void forgotPassword(String email, String clientIp);

    String verifyOtp(String email, String otp);

    void resetPassword(String resetToken, String newPassword);
}
