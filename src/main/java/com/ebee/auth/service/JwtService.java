package com.ebee.auth.service;

import com.ebee.auth.config.JwtProperties;
import com.ebee.user.enums.RoleType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
@Slf4j
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Authentication authentication) {
        String email = authentication.getName();
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_AGENT");

        return Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .claim("type", "access")
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date())
                .setNotBefore(new Date(System.currentTimeMillis() - 30_000))
                .setExpiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiration()))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(Authentication authentication) {
        String email = authentication.getName();

        return Jwts.builder()
                .setSubject(email)
                .claim("type", "refresh")
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date())
                .setNotBefore(new Date(System.currentTimeMillis() - 30_000))
                .setExpiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshExpiration()))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public RoleType extractRole(String token) {
        Claims claims = extractAllClaims(token);
        String role = claims.get("role", String.class);
        try {
            return RoleType.valueOf(role.replace("ROLE_", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public LocalDateTime getExpirationDate(String token) {
        Date expiration = extractExpiration(token);
        return LocalDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault());
    }

    public String extractEmailFromToken(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String email = extractEmail(token);
            return (email.equals(userDetails.getUsername())) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshTokenValid(String refreshToken, UserDetails userDetails) {
        final String email = extractEmailFromToken(refreshToken);
        return (email.equals(userDetails.getUsername())) && !isTokenExpired(refreshToken);
    }

    public String generateResetToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date())
                .setNotBefore(new Date(System.currentTimeMillis() - 30_000))
                .setExpiration(new Date(System.currentTimeMillis() + 300_000))
                .signWith(getResetSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmailFromResetToken(String token) {
        try {
            return extractClaim(token, getResetSigningKey(), Claims::getSubject);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or expired reset link");
        }
    }

    private Key getResetSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getResetSecret().getBytes(StandardCharsets.UTF_8));
    }

    private <T> T extractClaim(String token, Key signingKey, Function<Claims, T> claimsResolver) {
        final Claims claims = Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimsResolver.apply(claims);
    }
}
