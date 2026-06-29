package com.ebee.driver.service;

import com.ebee.driver.dto.DriverLocationRequest;
import com.ebee.driver.dto.DriverLocationResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DriverLocationService {

    private static final String LOCATION_KEY_PREFIX = "driver:location:";
    private static final long LOCATION_TTL_MINUTES = 5;

    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public DriverLocationResponse updateLocation(UUID driverId, String driverName, DriverLocationRequest request) {
        var response = new DriverLocationResponse(
            driverId,
            driverName,
            request.latitude(),
            request.longitude(),
            request.accuracy(),
            request.bearing(),
            request.speed(),
            Instant.now()
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

        messagingTemplate.convertAndSend("/topic/driver-locations", response);

        return response;
    }

    public Optional<DriverLocationResponse> getCurrentLocation(UUID driverId) {
        String json = stringRedisTemplate.opsForValue().get(LOCATION_KEY_PREFIX + driverId);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, DriverLocationResponse.class));
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
                    locations.add(objectMapper.readValue(json, DriverLocationResponse.class));
                } catch (JsonProcessingException ignored) {
                }
            }
        }
        return locations;
    }

    public void removeLocation(UUID driverId) {
        stringRedisTemplate.delete(LOCATION_KEY_PREFIX + driverId);
    }
}
