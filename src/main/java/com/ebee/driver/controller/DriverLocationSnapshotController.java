package com.ebee.driver.controller;

import com.ebee.common.exception.RateLimitException;
import com.ebee.driver.dto.DriverLocationHistoryResponse;
import com.ebee.driver.dto.DriverLocationRequest;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.service.DriverLocationService;
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

@Slf4j
@Controller
@RequiredArgsConstructor
public class DriverLocationSnapshotController {

    private final DriverLocationService driverLocationService;

    @MessageMapping("/driver-locations/snapshot")
    @SendToUser("/queue/driver-locations-snapshot")
    public List<DriverLocationHistoryResponse> requestSnapshot(Principal principal) {
        String email = principal != null ? principal.getName() : "anonymous";
        List<DriverLocationHistoryResponse> locations = driverLocationService.getActiveLocations();
        log.info("Snapshot requested by {} — returning {} locations", email, locations.size());
        return locations;
    }

    @MessageMapping("/driver-locations/update")
    public void updateLocation(@Valid @Payload DriverLocationRequest request, Principal principal) {
        if (principal == null) {
            log.warn("STOMP location update rejected: no principal");
            return;
        }
        String email = principal.getName();
        Driver driver = driverLocationService.resolveDriverByEmail(email).orElse(null);
        if (driver == null) {
            log.warn("STOMP location update rejected: driver not found for {}", email);
            return;
        }
        String driverName = driver.getUser().getFirstName() + " " + driver.getUser().getLastName();
        driverLocationService.updateLocation(driver.getId(), driverName, request);
        log.debug("STOMP location updated for driver {} ({})", driverName, driver.getId());
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
