package com.ebee.driver.service;

import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.notification.NotificationService;
import com.ebee.driver.dto.AvailabilityRequest;
import com.ebee.driver.dto.DriverRequest;
import com.ebee.driver.dto.DriverResponse;
import com.ebee.driver.dto.DriverWithCredentialsResponse;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.enums.DriverAvailability;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.enums.UserStatus;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$%";
    private static final int PASSWORD_LENGTH = 12;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public DriverWithCredentialsResponse createDriver(DriverRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        String generatedPassword = generatePassword();

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(generatedPassword))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .role(RoleType.DRIVER)
                .status(UserStatus.ACTIVE)
                .isActive(true)
                .mustChangePassword(true)
                .build();

        User savedUser = userRepository.save(user);

        Driver driver = Driver.builder()
                .user(savedUser)
                .licenseNumber(request.getLicenseNumber())
                .contactNumber(request.getContactNumber())
                .build();

        Driver savedDriver = driverRepository.save(driver);

        String subject = "Welcome to Ebee – Driver Login Credentials";
        String message = """
                Dear %s,
                
                Your driver account has been created.
                
                Email: %s
                Password: %s
                
                Please log in and change your password.
                """.formatted(savedUser.getFirstName(), savedUser.getEmail(), generatedPassword);
        auditLogService.record(AuditAction.DRIVER_CREATE, "Driver", savedDriver.getId(),
                "Driver " + savedUser.getFirstName() + " " + savedUser.getLastName()
                + " (" + savedUser.getEmail() + ") created");

        notificationService.notifyUser(savedUser, subject, message);

        return DriverWithCredentialsResponse.builder()
                .id(savedDriver.getId())
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .licenseNumber(savedDriver.getLicenseNumber())
                .contactNumber(savedDriver.getContactNumber())
                .availability(savedDriver.getAvailability())
                .isActive(savedDriver.getIsActive())
                .createdAt(savedDriver.getCreatedAt())
                .generatedPassword(generatedPassword)
                .build();
    }

    @Transactional(readOnly = true)
    public DriverResponse getDriver(UUID id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", id));
        return toResponse(driver);
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> getDrivers(DriverAvailability availability) {
        List<Driver> drivers;
        if (availability != null) {
            drivers = driverRepository.findByAvailability(availability);
        } else {
            drivers = driverRepository.findAll();
        }
        return drivers.stream().map(this::toResponse).toList();
    }

    @Transactional
    public DriverResponse updateDriver(UUID id, DriverRequest request) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", id));

        User user = driver.getUser();
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        userRepository.save(user);

        driver.setLicenseNumber(request.getLicenseNumber());
        driver.setContactNumber(request.getContactNumber());

        Driver savedDriver = driverRepository.save(driver);
        auditLogService.record(AuditAction.DRIVER_UPDATE, "Driver", id,
                "Driver " + driver.getUser().getEmail() + " updated");
        return toResponse(savedDriver);
    }

    @Transactional
    public DriverResponse updateAvailability(UUID id, AvailabilityRequest request) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", id));

        driver.setAvailability(request.getAvailability());
        Driver savedDriver = driverRepository.save(driver);
        auditLogService.record(AuditAction.DRIVER_AVAILABILITY_CHANGE, "Driver", id,
                "Driver " + driver.getUser().getEmail() + " availability changed to "
                + request.getAvailability());
        return toResponse(savedDriver);
    }

    @Transactional(readOnly = true)
    public Driver getDriverEntityByUserId(UUID userId) {
        return driverRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "userId", userId));
    }

    private DriverResponse toResponse(Driver driver) {
        DriverResponse response = new DriverResponse();
        response.setId(driver.getId());
        response.setUserId(driver.getUser().getId());
        response.setEmail(driver.getUser().getEmail());
        response.setFirstName(driver.getUser().getFirstName());
        response.setLastName(driver.getUser().getLastName());
        response.setUserName(driver.getUser().getFirstName() + " " + driver.getUser().getLastName());
        response.setUserEmail(driver.getUser().getEmail());
        response.setUserPhone(driver.getUser().getPhoneNumber());
        response.setLicenseNumber(driver.getLicenseNumber());
        response.setContactNumber(driver.getContactNumber());
        response.setAvailability(driver.getAvailability());
        response.setIsActive(driver.getIsActive());
        response.setCreatedAt(driver.getCreatedAt());
        response.setUpdatedAt(driver.getUpdatedAt());
        return response;
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }
}
