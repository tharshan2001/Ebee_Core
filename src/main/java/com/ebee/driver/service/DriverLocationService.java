package com.ebee.driver.service;

import com.ebee.common.exception.RateLimitException;
import com.ebee.driver.dto.DriverLocationHistoryResponse;
import com.ebee.driver.dto.DriverLocationPoint;
import com.ebee.driver.dto.DriverLocationRequest;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.user.service.UserLookupService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
@Transactional(readOnly = true)
public class DriverLocationService {

    private static final String LOCATION_KEY_PREFIX = "driver:location:history:";
    private static final String SEQ_KEY_PREFIX = "driver:location:seq:";
    private static final String RATE_LIMIT_KEY_PREFIX = "driver:ratelimit:";
    private static final String LAST_BROADCAST_KEY_PREFIX = "driver:location:lastbroadcast:";
    private static final int MAX_HISTORY_POINTS = 20;
    private static final long LOCATION_TTL_MINUTES = 5;
    private static final long RATE_LIMIT_MS = 500;
    private static final double MIN_MOVEMENT_METERS = 5.0;
    private static final long MIN_BROADCAST_INTERVAL_MS = 200;

    private static final double EARTH_RADIUS_METERS = 6371000;

    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final DriverRepository driverRepository;
    private final UserLookupService userLookupService;

    public DriverLocationHistoryResponse updateLocation(UUID driverId, String driverName, DriverLocationRequest request) {
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

        String photoUrl = driverRepository.findById(driverId)
            .map(driver -> driver.getUser().getProfilePictureUrl())
            .orElse(null);

        String locationKey = LOCATION_KEY_PREFIX + driverId;
        String seqKey = SEQ_KEY_PREFIX + driverId;

        long newSeq = stringRedisTemplate.opsForValue().increment(seqKey);

        DriverLocationPoint newPoint = new DriverLocationPoint(
            request.latitude(),
            request.longitude(),
            request.bearing(),
            request.speed(),
            request.accuracy(),
            Instant.now(),
            newSeq
        );

        try {
            List<DriverLocationPoint> points = getPointsList(locationKey);
            points.add(0, newPoint);
            if (points.size() > MAX_HISTORY_POINTS) {
                points = points.subList(0, MAX_HISTORY_POINTS);
            }
            String json = objectMapper.writeValueAsString(points);
            stringRedisTemplate.opsForValue().set(locationKey, json, LOCATION_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize location history", e);
        }

        List<DriverLocationPoint> currentPoints = getPointsList(locationKey);

        if (currentPoints.size() > 1) {
            DriverLocationPoint latest = currentPoints.get(0);
            DriverLocationPoint previous = currentPoints.get(1);
            double distance = haversineDistance(
                previous.latitude(), previous.longitude(),
                latest.latitude(), latest.longitude()
            );

            if (distance < MIN_MOVEMENT_METERS) {
                log.debug("Driver {} moved only {:.2f}m, skipping broadcast", driverId, distance);
                return new DriverLocationHistoryResponse(driverId, driverName, photoUrl, currentPoints, newSeq);
            }
        }

        String lastBroadcastKey = LAST_BROADCAST_KEY_PREFIX + driverId;
        String lastBroadcastStr = stringRedisTemplate.opsForValue().get(lastBroadcastKey);
        long lastBroadcastTime = lastBroadcastStr != null ? Long.parseLong(lastBroadcastStr) : 0;
        long now = System.currentTimeMillis();

        if (now - lastBroadcastTime < MIN_BROADCAST_INTERVAL_MS) {
            log.debug("Driver {} broadcast too soon ({})ms, skipping", driverId, now - lastBroadcastTime);
            return new DriverLocationHistoryResponse(driverId, driverName, photoUrl, currentPoints, newSeq);
        }

        var historyResponse = new DriverLocationHistoryResponse(
            driverId,
            driverName,
            photoUrl,
            currentPoints,
            newSeq
        );

        try {
            messagingTemplate.convertAndSend("/topic/driver-locations", historyResponse);
            log.info("Broadcast location history for driver {} ({} points, seq={}) at ({}, {})",
                driverId, historyResponse.points().size(), newSeq, request.latitude(), request.longitude());
            stringRedisTemplate.opsForValue().set(lastBroadcastKey, String.valueOf(now), 1, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Failed to broadcast location for driver {}: {}", driverId, e.getMessage());
        }

        return historyResponse;
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

    private List<DriverLocationPoint> getPointsList(String locationKey) {
        String json = stringRedisTemplate.opsForValue().get(locationKey);
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<DriverLocationPoint>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize location history: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public Optional<DriverLocationHistoryResponse> getLocationHistory(UUID driverId) {
        String locationKey = LOCATION_KEY_PREFIX + driverId;
        String seqKey = SEQ_KEY_PREFIX + driverId;
        String json = stringRedisTemplate.opsForValue().get(locationKey);
        if (json == null) return Optional.empty();

        try {
            List<DriverLocationPoint> points = objectMapper.readValue(json, new TypeReference<List<DriverLocationPoint>>() {});
            if (points.isEmpty()) return Optional.empty();

            DriverLocationPoint latest = points.get(0);
            String photoUrl = driverRepository.findById(driverId)
                .map(driver -> driver.getUser().getProfilePictureUrl())
                .orElse(null);

            String driverName = driverRepository.findById(driverId)
                .map(d -> d.getUser().getFirstName() + " " + d.getUser().getLastName())
                .orElse("Unknown");

            String seqStr = stringRedisTemplate.opsForValue().get(seqKey);
            long seq = seqStr != null ? Long.parseLong(seqStr) : latest.seq();

            return Optional.of(new DriverLocationHistoryResponse(driverId, driverName, photoUrl, points, seq));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize location history for driver {}: {}", driverId, e.getMessage());
            return Optional.empty();
        }
    }

    public List<DriverLocationHistoryResponse> getActiveLocations() {
        List<DriverLocationHistoryResponse> locations = new ArrayList<>();

        ScanOptions options = ScanOptions.scanOptions()
                .match(LOCATION_KEY_PREFIX + "*")
                .count(100)
                .build();

        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String driverIdStr = key.replace(LOCATION_KEY_PREFIX, "");
                try {
                    UUID driverId = UUID.fromString(driverIdStr);
                    getLocationHistory(driverId).ifPresent(locations::add);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return locations;
    }

    public void removeLocation(UUID driverId) {
        stringRedisTemplate.delete(LOCATION_KEY_PREFIX + driverId);
        stringRedisTemplate.delete(SEQ_KEY_PREFIX + driverId);
    }

    public Optional<Driver> resolveDriverByEmail(String email) {
        return userLookupService.findByEmail(email)
                .flatMap(user -> driverRepository.findByUserId(user.getId()));
    }
}
