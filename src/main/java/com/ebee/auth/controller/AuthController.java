package com.ebee.auth.controller;

import com.ebee.auth.dto.AuthResponse;
import com.ebee.auth.dto.LoginRequest;
import com.ebee.auth.dto.RegisterRequest;
import com.ebee.auth.service.AuthService;
import com.ebee.common.response.ApiResponse;
import com.ebee.user.dto.UserResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);

        // Set JWT token in cookie
        Cookie jwtCookie = new Cookie("jwt_token", authResponse.getToken());
        jwtCookie.setHttpOnly(true);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(24 * 60 * 60); // 24 hours
        response.addCookie(jwtCookie);

        // Set refresh token in cookie
        Cookie refreshCookie = new Cookie("refresh_token", authResponse.getRefreshToken());
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/api/auth/refresh");
        refreshCookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
        response.addCookie(refreshCookie);

        return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        UserResponse userResponse = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", userResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response) {
        Cookie jwtCookie = new Cookie("jwt_token", null);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(0); // Delete cookie
        response.addCookie(jwtCookie);

        Cookie refreshCookie = new Cookie("refresh_token", null);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/api/auth/refresh");
        refreshCookie.setMaxAge(0); // Delete cookie
        response.addCookie(refreshCookie);

        return ResponseEntity.ok(ApiResponse.success("Logout successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        String refreshToken = null;

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refresh_token".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Refresh token not found"));
        }

        AuthResponse authResponse = authService.refreshToken(refreshToken);

        // Set new JWT token in cookie
        Cookie jwtCookie = new Cookie("jwt_token", authResponse.getToken());
        jwtCookie.setHttpOnly(true);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(24 * 60 * 60); // 24 hours
        response.addCookie(jwtCookie);

        // Set new refresh token in cookie
        Cookie newRefreshCookie = new Cookie("refresh_token", authResponse.getRefreshToken());
        newRefreshCookie.setHttpOnly(true);
        newRefreshCookie.setPath("/api/auth/refresh");
        newRefreshCookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
        response.addCookie(newRefreshCookie);

        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", authResponse));
    }
}
