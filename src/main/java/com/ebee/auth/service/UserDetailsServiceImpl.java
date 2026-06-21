package com.ebee.auth.service;

import com.ebee.auth.security.UserPrincipal;
import com.ebee.user.entity.User;
import com.ebee.user.enums.UserStatus;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        if (!user.getIsActive() || user.getStatus() != UserStatus.ACTIVE) {
            throw new DisabledException("User account is inactive or locked");
        }

        return new UserPrincipal(user);
    }
}
