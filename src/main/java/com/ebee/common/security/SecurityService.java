package com.ebee.common.security;

import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityService {

    private final UserRepository userRepository;

    public boolean isCurrentUser(UUID userId, Authentication authentication) {
        if (authentication == null) {
            authentication = SecurityContextHolder.getContext().getAuthentication();
        }
        if (authentication == null || authentication.getName() == null) {
            return false;
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(user -> user.getId().equals(userId))
                .orElse(false);
    }
}
