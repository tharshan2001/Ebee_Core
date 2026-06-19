package com.ebee.seed;

import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.enums.UserStatus;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SuperAdminProperties properties;

    @Override
    public void run(String... args) {
        if (properties.getPassword() == null || properties.getPassword().isBlank()) {
            log.warn("SUPER_ADMIN_PASSWORD not set — skipping super admin creation");
            return;
        }

        if (userRepository.findByEmail(properties.getEmail()).isPresent()) {
            log.info("Super admin already exists: {}", properties.getEmail());
            return;
        }

        User superAdmin = User.builder()
                .email(properties.getEmail())
                .phoneNumber(properties.getPhoneNumber())
                .password(passwordEncoder.encode(properties.getPassword()))
                .firstName(properties.getFirstName())
                .lastName(properties.getLastName())
                .role(RoleType.SUPER_ADMIN)
                .status(UserStatus.ACTIVE)
                .isActive(true)
                .mustChangePassword(false)
                .passwordChangedAt(LocalDateTime.now())
                .build();

        userRepository.save(superAdmin);
        log.info("Super admin created: {} / {}", properties.getEmail(), properties.getPassword());
    }
}
