package com.ebee.driver.controller;

import com.ebee.driver.dto.DriverLocationResponse;
import com.ebee.driver.service.DriverLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class DriverLocationSnapshotController {

    private final DriverLocationService driverLocationService;

    @MessageMapping("/driver-locations/snapshot")
    @SendToUser("/queue/driver-locations-snapshot")
    public List<DriverLocationResponse> requestSnapshot() {
        return driverLocationService.getActiveLocations();
    }
}
