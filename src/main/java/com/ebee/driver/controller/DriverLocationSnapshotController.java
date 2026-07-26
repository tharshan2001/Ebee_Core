package com.ebee.driver.controller;

import com.ebee.common.exception.RateLimitException;
import com.ebee.driver.dto.DriverLocationRequest;
import com.ebee.driver.dto.DriverLocationResponse;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.driver.service.DriverLocationService;
import com.ebee.user.entity.User;
import com.ebee.user.service.UserLookupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DriverLocationSnapshotController {

    private final DriverLocationService driverLocationService;
    private final UserLookupService userLookupService;
    private final DriverRepository driverRepository;

    @MessageMapping("/driver-locations/snapshot")
    @SendToUser("/queue/driver-locations-snapshot")
    public List<DriverLocationResponse> requestSnapshot() {
        return driverLocationService.getActiveLocations();
    }

    @MessageMapping("/driver-locations/update")
    public void updateLocation(@Valid @Payload DriverLocationRequest request, Principal principal) {
        if (principal == null) {
            log.warn("STOMP location update rejected: no principal");
            return;
        }
        String email = principal.getName();
        User user = userLookupService.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("STOMP location update rejected: user not found for {}", email);
            return;
        }
        Driver driver = driverRepository.findByUserId(user.getId()).orElse(null);
        if (driver == null) {
            log.warn("STOMP location update rejected: driver not found for userId {}", user.getId());
            return;
        }
        String driverName = user.getFirstName() + " " + user.getLastName();
        driverLocationService.updateLocation(driver.getId(), driverName, request);
    }

    @MessageExceptionHandler(RateLimitException.class)
    public void handleRateLimit(RateLimitException ex) {
        log.debug("STOMP rate limit: {}", ex.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    public void handleException(Exception ex) {
        log.warn("STOMP location update error: {}", ex.getMessage());
    }
}
