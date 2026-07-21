package com.ebee.auth.service;

import com.ebee.auth.config.JwtProperties;
import com.ebee.user.enums.RoleType;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final String SECRET = "this-is-a-test-secret-that-is-at-least-256-bits-long-for-hs256-test";
    private static final String RESET_SECRET = "this-is-a-different-test-secret-for-reset-tokens-at-least-256-bits";
    private static final long EXPIRATION = 86_400_000;
    private static final long REFRESH_EXPIRATION = 604_800_000;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(SECRET, RESET_SECRET, EXPIRATION, REFRESH_EXPIRATION);
        jwtService = new JwtService(properties);
    }

    private Authentication createAuthentication(String email, String role) {
        return new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void generateToken_returnsValidJwt() {
        Authentication auth = createAuthentication("admin@ebee.com", "ADMIN");
        String token = jwtService.generateToken(auth);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(jwtService.extractEmail(token)).isEqualTo("admin@ebee.com");
        assertThat(jwtService.extractRole(token)).isEqualTo(RoleType.ADMIN);
    }

    @Test
    void extractEmailFromToken_returnsSubject() {
        Authentication auth = createAuthentication("agent@ebee.com", "AGENT");
        String token = jwtService.generateToken(auth);

        assertThat(jwtService.extractEmailFromToken(token)).isEqualTo("agent@ebee.com");
    }

    @Test
    void getExpirationDate_returnsFutureDate() {
        Authentication auth = createAuthentication("admin@ebee.com", "ADMIN");
        String token = jwtService.generateToken(auth);

        LocalDateTime exp = jwtService.getExpirationDate(token);
        assertThat(exp).isAfter(LocalDateTime.now());
    }

    @Test
    void generateRefreshToken_returnsValidJwt() {
        Authentication auth = createAuthentication("admin@ebee.com", "ADMIN");
        String token = jwtService.generateRefreshToken(auth);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractEmailFromToken(token)).isEqualTo("admin@ebee.com");
    }

    @Test
    void isTokenValid_withMatchingUser_returnsTrue() {
        Authentication auth = createAuthentication("admin@ebee.com", "ADMIN");
        String token = jwtService.generateToken(auth);
        var userDetails = new org.springframework.security.core.userdetails.User(
                "admin@ebee.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void isTokenValid_withWrongUser_returnsFalse() {
        Authentication auth = createAuthentication("admin@ebee.com", "ADMIN");
        String token = jwtService.generateToken(auth);
        var userDetails = new org.springframework.security.core.userdetails.User(
                "other@ebee.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(jwtService.isTokenValid(token, userDetails)).isFalse();
    }

    @Test
    void isTokenValid_withExpiredToken_returnsFalse() {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .setSubject("admin@ebee.com")
                .claim("role", "ROLE_ADMIN")
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date(System.currentTimeMillis() - 10_000))
                .setExpiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        var userDetails = new org.springframework.security.core.userdetails.User(
                "admin@ebee.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(jwtService.isTokenValid(expiredToken, userDetails)).isFalse();
    }

    @Test
    void isRefreshTokenValid_withMatchingUser_returnsTrue() {
        Authentication auth = createAuthentication("admin@ebee.com", "ADMIN");
        String token = jwtService.generateRefreshToken(auth);
        var userDetails = new org.springframework.security.core.userdetails.User(
                "admin@ebee.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(jwtService.isRefreshTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void generateResetToken_createsValidToken() {
        String token = jwtService.generateResetToken("user@ebee.com");

        assertThat(token).isNotBlank();
        String email = jwtService.extractEmailFromResetToken(token);
        assertThat(email).isEqualTo("user@ebee.com");
    }

    @Test
    void extractEmailFromResetToken_withInvalidToken_throws() {
        assertThatThrownBy(() -> jwtService.extractEmailFromResetToken("invalid.token.here"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired reset link");
    }
}
