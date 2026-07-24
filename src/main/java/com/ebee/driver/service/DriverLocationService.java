package com.ebee.driver.service;

import com.ebee.driver.dto.DriverLocationRequest;
import com.ebee.driver.dto.DriverLocationResponse;
import com.ebee.driver.repository.DriverRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverLocationService {

    private static final String LOCATION_KEY_PREFIX = "driver:location:";
    private static final long LOCATION_TTL_MINUTES = 5;

    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final DriverRepository driverRepository;

    public DriverLocationResponse updateLocation(UUID driverId, String driverName, DriverLocationRequest request) {
        String photoUrl = driverRepository.findById(driverId)
            .map(driver -> driver.getUser().getProfilePictureUrl())
            .orElse(null);
        var response = new DriverLocationResponse(
            driverId,
            driverName,
            request.latitude(),
            request.longitude(),
            request.accuracy(),
            request.bearing(),
            request.speed(),
            Instant.now(),
            photoUrl
        );

        try {
            String json = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(
                LOCATION_KEY_PREFIX + driverId,
                json,
                LOCATION_TTL_MINUTES,
                TimeUnit.MINUTES
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize location", e);
        }

        try {
            messagingTemplate.convertAndSend("/topic/driver-locations", response);
        } catch (Exception e) {
            log.warn("Failed to broadcast location for driver {}: {}", driverId, e.getMessage());
        }

        return response;
    }

    public Optional<DriverLocationResponse> getCurrentLocation(UUID driverId) {
        String json = stringRedisTemplate.opsForValue().get(LOCATION_KEY_PREFIX + driverId);
        if (json == null) return Optional.empty();
        try {
            DriverLocationResponse loc = objectMapper.readValue(json, DriverLocationResponse.class);
            return Optional.of(enrichPhotoUrl(driverId, loc));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public List<DriverLocationResponse> getActiveLocations() {
        Set<String> keys = stringRedisTemplate.keys(LOCATION_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return List.of();

        List<DriverLocationResponse> locations = new ArrayList<>();
        for (String key : keys) {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                try {
                    DriverLocationResponse loc = objectMapper.readValue(json, DriverLocationResponse.class);
                    locations.add(enrichPhotoUrl(loc.driverId(), loc));
                } catch (JsonProcessingException ignored) {
                }
            }
        }
        return locations;
    }

    private DriverLocationResponse enrichPhotoUrl(UUID driverId, DriverLocationResponse loc) {
        if (loc.photoUrl() != null) return loc;
        String photoUrl = driverRepository.findById(driverId)
            .map(driver -> driver.getUser().getProfilePictureUrl())
            .orElse(null);
        if (photoUrl == null) return loc;
        return new DriverLocationResponse(
            loc.driverId(),
            loc.driverName(),
            loc.latitude(),
            loc.longitude(),
            loc.accuracy(),
            loc.bearing(),
            loc.speed(),
            loc.recordedAt(),
            photoUrl
        );
    }

    public void removeLocation(UUID driverId) {
        stringRedisTemplate.delete(LOCATION_KEY_PREFIX + driverId);
    }
}
