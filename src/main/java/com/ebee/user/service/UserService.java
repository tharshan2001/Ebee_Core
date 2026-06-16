package com.ebee.user.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.util.PasswordGenerator;
import com.ebee.common.util.PhoneUtils;
import com.ebee.notification.NotificationPort;
import com.ebee.user.dto.UserProfileResponse;
import com.ebee.user.dto.UserRequest;
import com.ebee.user.dto.UserResponse;
import com.ebee.user.dto.UserUpdateRequest;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.enums.UserStatus;
import com.ebee.user.mapper.UserMapper;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final NotificationPort notificationPort;

    @Transactional
    public UserResponse createUser(UserRequest request) {
        request.setPhoneNumber(PhoneUtils.normalize(request.getPhoneNumber()));
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        String rawPassword = request.getPassword();
        if (rawPassword == null || rawPassword.isEmpty()) {
            rawPassword = PasswordGenerator.generate();
        }

        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setMustChangePassword(rawPassword != request.getPassword());
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        User savedUser = userRepository.save(user);

        log.info("User created: {}", savedUser.getEmail());
        sendWelcomeNotification(savedUser, rawPassword);

        return userMapper.toResponse(savedUser);
    }

    private void sendWelcomeNotification(User user, String rawPassword) {
        try {
            String subject = "Welcome to Ebee — Account Created";
            Map<String, String> p = new HashMap<>();
            p.put("firstName", user.getFirstName());
            p.put("email", user.getEmail());
            p.put("password", rawPassword);
            notificationPort.notifyUser(user, subject, "user-welcome", p, Set.of());
        } catch (Exception e) {
            log.warn("Failed to send welcome notification to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return userMapper.toProfileResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        List<User> users = userRepository.findAll();
        return userMapper.toResponseList(users);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByRole(RoleType role) {
        List<User> users = userRepository.findByRole(role);
        return userMapper.toResponseList(users);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        request.setPhoneNumber(PhoneUtils.normalize(request.getPhoneNumber()));
        if (!user.getEmail().equals(request.getEmail()) &&
            userRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhoneNumber(request.getPhoneNumber());
        encodePasswordIfPresent(user, request.getPassword());

        User updatedUser = userRepository.save(user);
        return userMapper.toResponse(updatedUser);
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setIsActive(false);
        userRepository.save(user);
    }

    @Transactional
    public UserResponse activateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setStatus(UserStatus.ACTIVE);
        user.setIsActive(true);
        return userMapper.toResponse(userRepository.save(user));
    }

    private void encodePasswordIfPresent(User user, String rawPassword) {
        if (rawPassword != null && !rawPassword.isEmpty()) {
            user.setPassword(passwordEncoder.encode(rawPassword));
        }
    }
}
