package com.ebee.driver.service;

import com.ebee.audit.service.AuditLogService;
import com.ebee.common.security.SecurityService;
import com.ebee.common.util.PhoneUtils;
import com.ebee.driver.dto.AvailabilityRequest;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.enums.DriverAvailability;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.notification.NotificationPort;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock private DriverRepository driverRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationPort notificationPort;
    @Mock private AuditLogService auditLogService;
    @Mock private SecurityService securityService;

    @InjectMocks private DriverService driverService;

    @Test
    void updateAvailability_asAdmin_succeeds() {
        UUID driverId = UUID.randomUUID();
        UUID driverUserId = UUID.randomUUID();
        User driverUser = User.builder().id(driverUserId).email("driver@ebee.com").build();
        Driver driver = Driver.builder()
                .id(driverId)
                .user(driverUser)
                .availability(DriverAvailability.AVAILABLE)
                .build();
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(securityService.isAdmin()).thenReturn(true);
        when(driverRepository.save(any(Driver.class))).thenReturn(driver);

        AvailabilityRequest request = new AvailabilityRequest();
        request.setAvailability(DriverAvailability.ON_TRIP);

        var response = driverService.updateAvailability(driverId, request);

        assertThat(response).isNotNull();
        verify(driverRepository).save(driver);
    }

    @Test
    void updateAvailability_asOwnDriver_succeeds() {
        UUID driverId = UUID.randomUUID();
        UUID driverUserId = UUID.randomUUID();
        User driverUser = User.builder().id(driverUserId).email("driver@ebee.com").build();
        Driver driver = Driver.builder()
                .id(driverId)
                .user(driverUser)
                .availability(DriverAvailability.AVAILABLE)
                .build();
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.isCurrentUser(driverUserId, null)).thenReturn(true);
        when(driverRepository.save(any(Driver.class))).thenReturn(driver);

        AvailabilityRequest request = new AvailabilityRequest();
        request.setAvailability(DriverAvailability.ON_TRIP);

        var response = driverService.updateAvailability(driverId, request);

        assertThat(response).isNotNull();
        verify(driverRepository).save(driver);
    }

    @Test
    void updateAvailability_asOtherDriver_throwsAccessDenied() {
        UUID driverId = UUID.randomUUID();
        UUID driverUserId = UUID.randomUUID();
        User driverUser = User.builder().id(driverUserId).email("driver@ebee.com").build();
        Driver driver = Driver.builder()
                .id(driverId)
                .user(driverUser)
                .availability(DriverAvailability.AVAILABLE)
                .build();
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.isCurrentUser(driverUserId, null)).thenReturn(false);

        AvailabilityRequest request = new AvailabilityRequest();
        request.setAvailability(DriverAvailability.SUSPENDED);

        assertThatThrownBy(() -> driverService.updateAvailability(driverId, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("own availability");

        verify(driverRepository, never()).save(any(Driver.class));
    }
}
