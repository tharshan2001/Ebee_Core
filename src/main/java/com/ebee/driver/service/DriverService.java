package com.ebee.driver.service;

import com.ebee.alert.enums.AlertType;
import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.common.util.PasswordGenerator;
import com.ebee.common.util.PhoneUtils;
import com.ebee.notification.NotificationPort;
import com.ebee.notification.model.PushRequest;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
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
public class DriverService {

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationPort notificationPort;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;


    @Transactional
    public DriverWithCredentialsResponse createDriver(DriverRequest request) {
        request.setPhoneNumber(PhoneUtils.normalize(request.getPhoneNumber()));
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        String generatedPassword = PasswordGenerator.generate();

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
                .build();

        Driver savedDriver = driverRepository.save(driver);

        log.info("Driver created: {}", savedUser.getEmail());
        String subject = "Welcome to Ebee!";
        Map<String, String> p = new HashMap<>();
        p.put("firstName", savedUser.getFirstName());
        p.put("email", savedUser.getEmail());
        p.put("password", generatedPassword);
        notificationPort.notifyUser(savedUser, subject, "driver-welcome", p, Set.of());
        auditLogService.record(AuditAction.DRIVER_CREATE, "Driver", savedDriver.getId(),
                "Driver " + savedUser.getFirstName() + " " + savedUser.getLastName()
                + " (" + savedUser.getEmail() + ") created");

        return DriverWithCredentialsResponse.builder()
                .id(savedDriver.getId())
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .licenseNumber(savedDriver.getLicenseNumber())
                .availability(savedDriver.getAvailability())
                .isActive(savedDriver.getIsActive())
                .createdAt(savedDriver.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public DriverResponse getDriver(UUID id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", id));
        return toResponse(driver);
    }

    @Transactional(readOnly = true)
    public Page<DriverResponse> getDrivers(DriverAvailability availability, Pageable pageable) {
        Page<Driver> drivers;
        if (availability != null) {
            drivers = driverRepository.findByAvailability(availability, pageable);
        } else {
            drivers = driverRepository.findAll(pageable);
        }
        return drivers.map(this::toResponse);
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
            user.setPhoneNumber(PhoneUtils.normalize(request.getPhoneNumber()));
        }
        userRepository.save(user);

        driver.setLicenseNumber(request.getLicenseNumber());

        Driver savedDriver = driverRepository.save(driver);
        auditLogService.record(AuditAction.DRIVER_UPDATE, "Driver", id,
                "Driver " + driver.getUser().getEmail() + " updated");

        if (securityService.isAdmin()) {
            notificationPort.createInternalAlert(
                    driver.getUser(),
                    "Profile Updated",
                    "Your driver profile has been updated by an admin.",
                    AlertType.DRIVER_PROFILE_UPDATED,
                    id,
                    "DRIVER"
            );
            if (driver.getFcmToken() != null) {
                notificationPort.sendPush(new PushRequest(
                        driver.getFcmToken(),
                        "Profile Updated",
                        "Your driver profile has been updated by an admin.",
                        Map.of("type", "driver_profile_updated")
                ));
            }
        }

        return toResponse(savedDriver);
    }

    @Transactional
    public DriverResponse updateAvailability(UUID id, AvailabilityRequest request) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", id));

        if (!securityService.isAdmin() && !securityService.isCurrentUser(driver.getUser().getId(), null)) {
            throw new AccessDeniedException("You can only update your own availability");
        }

        DriverAvailability previousAvailability = driver.getAvailability();
        driver.setAvailability(request.getAvailability());
        Driver savedDriver = driverRepository.save(driver);
        auditLogService.record(AuditAction.DRIVER_AVAILABILITY_CHANGE, "Driver", id,
                "Driver " + driver.getUser().getEmail() + " availability changed from "
                + previousAvailability + " to " + request.getAvailability());

        if (securityService.isAdmin()) {
            if (request.getAvailability() == DriverAvailability.SUSPENDED) {
                notificationPort.createInternalAlert(
                        driver.getUser(),
                        "Account Suspended",
                        "Your driver account has been suspended. Please contact support.",
                        AlertType.DRIVER_SUSPENDED,
                        id,
                        "DRIVER"
                );
                if (driver.getFcmToken() != null) {
                    notificationPort.sendPush(new PushRequest(
                            driver.getFcmToken(),
                            "Account Suspended",
                            "Your driver account has been suspended. Please contact support.",
                            Map.of("type", "driver_suspended")
                    ));
                }
            } else if (previousAvailability == DriverAvailability.SUSPENDED
                    && request.getAvailability() == DriverAvailability.AVAILABLE) {
                notificationPort.createInternalAlert(
                        driver.getUser(),
                        "Account Reactivated",
                        "Your driver account has been reactivated. You can now log in and accept deliveries.",
                        AlertType.DRIVER_UNSUSPENDED,
                        id,
                        "DRIVER"
                );
                if (driver.getFcmToken() != null) {
                    notificationPort.sendPush(new PushRequest(
                            driver.getFcmToken(),
                            "Account Reactivated",
                            "Your driver account has been reactivated. You can now log in and accept deliveries.",
                            Map.of("type", "driver_unsuspended")
                    ));
                }
            }
        }

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
        response.setAvailability(driver.getAvailability());
        response.setIsActive(driver.getIsActive());
        response.setCreatedAt(driver.getCreatedAt());
        response.setUpdatedAt(driver.getUpdatedAt());
        return response;
    }

}
