package com.ebee.auth.security;

import com.ebee.auth.service.JwtService;
import com.ebee.auth.service.UserDetailsServiceImpl;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.SecurityException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Keep in sync with public endpoints in SecurityConfig.
        // The filter skips JWT parsing for public paths; SecurityConfig still enforces method-level authorization.
        return path.startsWith("/actuator/health")
                || path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/refresh")
                || path.startsWith("/api/auth/forgot-password")
                || path.startsWith("/api/auth/verify-otp")
                || path.startsWith("/api/auth/reset-password")
                // Product reads are public too, but still parse the token: what they show depends on who asks.
                || ("GET".equals(request.getMethod()) && (path.startsWith("/api/categories") || path.startsWith("/api/combos")))
                || path.startsWith("/api/agent-applications")
                || path.startsWith("/api/whatsapp/webhook")
                || path.startsWith("/ws")
                || path.startsWith("/raw-ws")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }

    private boolean isProductRead(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "GET".equals(request.getMethod()) && path.startsWith("/api/products");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        if (jwt.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String email = jwtService.extractEmail(jwt);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired for request {}: {}", request.getServletPath(), e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT for request {}: {}", request.getServletPath(), e.getMessage());
        } catch (SecurityException e) {
            log.warn("Invalid JWT for request {}: {}", request.getServletPath(), e.getMessage());
        } catch (Exception e) {
            log.warn("JWT processing error for request {}: {}", request.getServletPath(), e.getMessage());
        }

        // A signed-in client with a stale token must be told to refresh it, not quietly given the
        // trimmed public view of a product (which would look like everything is out of stock).
        if (SecurityContextHolder.getContext().getAuthentication() == null && isProductRead(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

}
