package com.ebee.auth.security;

import com.ebee.auth.service.IpWhitelistService;
import com.ebee.common.util.RequestUtils;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class IpWhitelistFilter extends OncePerRequestFilter {

    private final IpWhitelistService ipWhitelistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal principal) {

            User user = principal.getUser();
            RoleType role = user.getRole();

            if (role == RoleType.ADMIN || role == RoleType.SUPER_ADMIN) {
                String clientIp = RequestUtils.getClientIp(request);

                if (!ipWhitelistService.isAllowed(user, clientIp)) {
                    log.warn("IP whitelist blocked: user={} ({}) role={} ip={}",
                            user.getEmail(), user.getId(), role, clientIp);

                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"success\":false,\"message\":\"IP is not whitelisted. Your IP: " + clientIp + "\"}");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
