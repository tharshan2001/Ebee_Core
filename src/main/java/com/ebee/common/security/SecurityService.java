package com.ebee.common.security;

import com.ebee.auth.security.UserPrincipal;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.service.UserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityService {

    private final UserLookupService userLookupService;

    public boolean isCurrentUser(UUID userId, Authentication authentication) {
        User user = getCurrentUser(authentication);
        return user != null && user.getId().equals(userId);
    }

    public User getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            authentication = SecurityContextHolder.getContext().getAuthentication();
        }
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getUser();
        }
        return userLookupService.findByEmail(authentication.getName()).orElse(null);
    }

    public UUID getCurrentUserId(Authentication authentication) {
        User user = getCurrentUser(authentication);
        return user != null ? user.getId() : null;
    }

    public UUID getCurrentUserId() {
        return getCurrentUserId(null);
    }

    public boolean isAdmin() {
        return hasRole("ROLE_ADMIN") || hasRole("ROLE_SUPER_ADMIN");
    }

    public boolean isSuperAdmin() {
        return hasRole("ROLE_SUPER_ADMIN");
    }

    private boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals(role));
    }

    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return "system";
        }
        return authentication.getName();
    }

    public String getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "SYSTEM";
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(a -> a.replace("ROLE_", ""))
                .orElse("UNKNOWN");
    }
}
