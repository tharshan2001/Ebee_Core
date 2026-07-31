package com.ebee.driver.service;

import com.ebee.common.exception.RateLimitException;
import com.ebee.driver.dto.DriverLocationResponse;
import com.ebee.driver.dto.DriverLocationRequest;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.user.service.UserLookupService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverLocationService {

    private static final String LOCATION_KEY_PREFIX = "driver:location:";
    private static final String SEQ_KEY_PREFIX = "driver:location:seq:";
    private static final String RATE_LIMIT_KEY_PREFIX = "driver:ratelimit:";
    private static final String LAST_UPDATE_KEY_PREFIX = "driver:location:lastupdate:";
    private static final long LOCATION_TTL_MINUTES = 5;
    private static final long RATE_LIMIT_MS = 100;

    private static final double EARTH_RADIUS_METERS = 6371000;

    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final DriverRepository driverRepository;
    private final UserLookupService userLookupService;

    private record RedisLocationData(
        UUID driverId,
        String driverName,
        String photoUrl,
        double latitude,
        double longitude,
        Float bearing,
        Float speed,
        Float accuracy,
        Instant timestamp,
        long seq
    ) {}

    @Transactional
    public DriverLocationResponse updateLocation(UUID driverId, String driverName, DriverLocationRequest request) {
        String rateKey = RATE_LIMIT_KEY_PREFIX + driverId;
        String rateVal = stringRedisTemplate.opsForValue().get(rateKey);
        if (rateVal != null) {
            long previous = Long.parseLong(rateVal);
            long now = System.currentTimeMillis();
            if ((now - previous) < RATE_LIMIT_MS) {
                throw new RateLimitException("Location update too frequent; minimum interval is " + RATE_LIMIT_MS + "ms");
            }
        }
        stringRedisTemplate.opsForValue().set(rateKey, String.valueOf(System.currentTimeMillis()), 5, TimeUnit.SECONDS);

        String locationKey = LOCATION_KEY_PREFIX + driverId;
        String seqKey = SEQ_KEY_PREFIX + driverId;

        long newSeq = stringRedisTemplate.opsForValue().increment(seqKey);
        long now = System.currentTimeMillis();

        String photoUrl = driverRepository.findById(driverId)
            .map(driver -> driver.getUser().getProfilePictureUrl())
            .orElse(null);

        double newLat = request.latitude();
        double newLng = request.longitude();
        Float newBearing = request.bearing();
        Float newSpeed = request.speed();
        Instant timestamp = Instant.now();

        try {
            var redisData = new RedisLocationData(
                driverId, driverName, photoUrl, newLat, newLng,
                newBearing, newSpeed, request.accuracy(), timestamp, newSeq
            );
            String json = objectMapper.writeValueAsString(redisData);
            stringRedisTemplate.opsForValue().set(locationKey, json, LOCATION_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize location", e);
        }

        stringRedisTemplate.opsForValue().set(LAST_UPDATE_KEY_PREFIX + driverId, String.valueOf(now), LOCATION_TTL_MINUTES, TimeUnit.MINUTES);

        DriverLocationResponse response = buildResponse(driverId, driverName, photoUrl, newLat, newLng,
            newBearing, newSpeed, request.accuracy(), timestamp, newSeq);

        messagingTemplate.convertAndSend("/topic/driver-locations", response);
        log.info("Broadcast driver {} at ({}, {}) speed={}km/h seq={}",
            driverId, newLat, newLng, newSpeed, newSeq);

        return response;
    }

    private float normalizeAngle(float angle) {
        angle = angle % 360;
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private DriverLocationResponse buildResponse(UUID driverId, String driverName, String photoUrl,
            double latitude, double longitude, Float bearing, Float speed, Float accuracy,
            Instant recordedAt, long seq) {
        return new DriverLocationResponse(
            driverId,
            driverName,
            photoUrl,
            latitude,
            longitude,
            bearing,
            speed,
            accuracy,
            recordedAt,
            seq
        );
    }

    @Transactional(readOnly = true)
    public Optional<DriverLocationResponse> getLocation(UUID driverId) {
        String locationKey = LOCATION_KEY_PREFIX + driverId;
        String json = stringRedisTemplate.opsForValue().get(locationKey);
        if (json == null) return Optional.empty();

        try {
            RedisLocationData data = objectMapper.readValue(json, RedisLocationData.class);
            return Optional.of(buildResponse(
                data.driverId(),
                data.driverName(),
                data.photoUrl(),
                data.latitude(),
                data.longitude(),
                data.bearing(),
                data.speed(),
                data.accuracy(),
                data.timestamp(),
                data.seq()
            ));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize location for driver {}: {}", driverId, e.getMessage());
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<DriverLocationResponse> getActiveLocations() {
        List<DriverLocationResponse> locations = new ArrayList<>();

        ScanOptions options = ScanOptions.scanOptions()
                .match(LOCATION_KEY_PREFIX + "*")
                .count(100)
                .build();

        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (key.endsWith(":seq") || key.endsWith(":lastupdate")) continue;
                String driverIdStr = key.replace(LOCATION_KEY_PREFIX, "");
                try {
                    UUID driverId = UUID.fromString(driverIdStr);
                    getLocation(driverId).ifPresent(locations::add);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return locations;
    }

    @Transactional
    public void removeLocation(UUID driverId) {
        stringRedisTemplate.delete(LOCATION_KEY_PREFIX + driverId);
        stringRedisTemplate.delete(SEQ_KEY_PREFIX + driverId);
        stringRedisTemplate.delete(LAST_UPDATE_KEY_PREFIX + driverId);
    }

    public Optional<Driver> resolveDriverByEmail(String email) {
        return userLookupService.findByEmail(email)
                .flatMap(user -> driverRepository.findByUserId(user.getId()));
    }
}
