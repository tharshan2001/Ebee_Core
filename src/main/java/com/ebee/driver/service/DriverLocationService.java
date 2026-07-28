package com.ebee.driver.service;

import com.ebee.common.exception.RateLimitException;
import com.ebee.driver.dto.DriverLocationResponse;
import com.ebee.driver.dto.DriverLocationRequest;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.user.service.UserLookupService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverLocationService {

    private static final String LOCATION_KEY_PREFIX = "driver:location:";
    private static final String SEQ_KEY_PREFIX = "driver:location:seq:";
    private static final String RATE_LIMIT_KEY_PREFIX = "driver:ratelimit:";
    private static final String LAST_UPDATE_KEY_PREFIX = "driver:location:lastupdate:";
    private static final long LOCATION_TTL_MINUTES = 5;
    private static final long RATE_LIMIT_MS = 300;
    private static final double MIN_MOVEMENT_METERS = 3.0;
    private static final float MIN_SPEED_KMH = 1.0f;
    private static final float MIN_HEADING_CHANGE_DEGREES = 5.0f;

    private static final long BROADCAST_INTERVAL_MS = 1000;
    private static final long SPEED_TIER_STOPPED_MS = 5000;
    private static final long SPEED_TIER_SLOW_MS = 2000;
    private static final long SPEED_TIER_NORMAL_MS = 1000;
    private static final long SPEED_TIER_FAST_MS = 500;

    private static final double EARTH_RADIUS_METERS = 6371000;

    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final DriverRepository driverRepository;
    private final UserLookupService userLookupService;

    private final ConcurrentHashMap<UUID, PendingBroadcast> pendingBroadcasts = new ConcurrentHashMap<>();

    record PendingBroadcast(
        UUID driverId,
        String driverName,
        String photoUrl,
        double latitude,
        double longitude,
        Float bearing,
        Float speed,
        Float accuracy,
        Instant timestamp,
        long seq,
        Float lastBroadcastBearing,
        long lastUpdateTime
    ) {}

    @PostConstruct
    public void init() {
        log.info("DriverLocationService initialized with broadcast interval {}ms", BROADCAST_INTERVAL_MS);
    }

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

        PendingBroadcast existing = pendingBroadcasts.get(driverId);
        String photoUrl = driverRepository.findById(driverId)
            .map(driver -> driver.getUser().getProfilePictureUrl())
            .orElse(null);

        double newLat = request.latitude();
        double newLng = request.longitude();
        Float newBearing = request.bearing();
        Float newSpeed = request.speed();

        Float filteredBearing = newBearing;
        if (existing != null && newSpeed != null && newSpeed < MIN_SPEED_KMH) {
            Float lastGoodBearing = existing.lastBroadcastBearing;
            if (lastGoodBearing != null && newBearing != null) {
                float bearingDiff = Math.abs(normalizeAngle(newBearing - lastGoodBearing));
                if (bearingDiff < MIN_HEADING_CHANGE_DEGREES) {
                    filteredBearing = lastGoodBearing;
                }
            }
        }

        if (existing != null) {
            double distance = haversineDistance(existing.latitude, existing.longitude, newLat, newLng);
            if (distance < MIN_MOVEMENT_METERS && existing.speed != null && existing.speed < MIN_SPEED_KMH && newSpeed != null && newSpeed < MIN_SPEED_KMH) {
                log.debug("Driver {} stationary, skipping position update", driverId);
                return buildResponse(driverId, driverName, photoUrl, existing.latitude, existing.longitude,
                    filteredBearing, newSpeed, request.accuracy(), Instant.now(), newSeq);
            }
        }

        Instant timestamp = Instant.now();

        PendingBroadcast pending = new PendingBroadcast(
            driverId,
            driverName,
            photoUrl,
            newLat,
            newLng,
            filteredBearing,
            newSpeed,
            request.accuracy(),
            timestamp,
            newSeq,
            existing != null ? existing.lastBroadcastBearing : filteredBearing,
            now
        );
        pendingBroadcasts.put(driverId, pending);

        try {
            String json = objectMapper.writeValueAsString(pending);
            stringRedisTemplate.opsForValue().set(locationKey, json, LOCATION_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize location", e);
        }

        stringRedisTemplate.opsForValue().set(LAST_UPDATE_KEY_PREFIX + driverId, String.valueOf(now), LOCATION_TTL_MINUTES, TimeUnit.MINUTES);

        if (existing != null && existing.lastBroadcastBearing != null && filteredBearing != null) {
            float bearingDiff = Math.abs(normalizeAngle(filteredBearing - existing.lastBroadcastBearing));
            if (bearingDiff >= MIN_HEADING_CHANGE_DEGREES) {
                pendingBroadcasts.put(driverId, new PendingBroadcast(
                    driverId, driverName, photoUrl, newLat, newLng,
                    filteredBearing, newSpeed, request.accuracy(), timestamp, newSeq,
                    filteredBearing, now
                ));
            }
        }

        return buildResponse(driverId, driverName, photoUrl, newLat, newLng,
            filteredBearing, newSpeed, request.accuracy(), timestamp, newSeq);
    }

    @Scheduled(fixedRate = BROADCAST_INTERVAL_MS)
    public void broadcastLocations() {
        long now = System.currentTimeMillis();
        List<DriverLocationResponse> toBroadcast = new ArrayList<>();

        for (PendingBroadcast pending : pendingBroadcasts.values()) {
            long timeSinceUpdate = now - pending.lastUpdateTime();
            long broadcastInterval = getBroadcastInterval(pending.speed());

            if (timeSinceUpdate >= broadcastInterval) {
                DriverLocationResponse response = buildResponse(
                    pending.driverId(),
                    pending.driverName(),
                    pending.photoUrl(),
                    pending.latitude(),
                    pending.longitude(),
                    pending.bearing(),
                    pending.speed(),
                    pending.accuracy(),
                    pending.timestamp(),
                    pending.seq()
                );
                toBroadcast.add(response);

                pendingBroadcasts.put(pending.driverId(), new PendingBroadcast(
                    pending.driverId(),
                    pending.driverName(),
                    pending.photoUrl(),
                    pending.latitude(),
                    pending.longitude(),
                    pending.bearing(),
                    pending.speed(),
                    pending.accuracy(),
                    pending.timestamp(),
                    pending.seq(),
                    pending.bearing(),
                    pending.lastUpdateTime()
                ));
            }
        }

        if (!toBroadcast.isEmpty()) {
            try {
                for (DriverLocationResponse response : toBroadcast) {
                    messagingTemplate.convertAndSend("/topic/driver-locations", response);
                    log.debug("Broadcast driver {} at ({}, {}) speed={}km/h",
                        response.driverId(), response.latitude(), response.longitude(), response.speed());
                }
            } catch (Exception e) {
                log.warn("Failed to broadcast locations: {}", e.getMessage());
            }
        }
    }

    private long getBroadcastInterval(Float speed) {
        if (speed == null || speed < MIN_SPEED_KMH) {
            return SPEED_TIER_STOPPED_MS;
        } else if (speed < 20.0f) {
            return SPEED_TIER_SLOW_MS;
        } else if (speed < 60.0f) {
            return SPEED_TIER_NORMAL_MS;
        } else {
            return SPEED_TIER_FAST_MS;
        }
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

    public Optional<DriverLocationResponse> getLocation(UUID driverId) {
        String locationKey = LOCATION_KEY_PREFIX + driverId;
        String json = stringRedisTemplate.opsForValue().get(locationKey);
        if (json == null) return Optional.empty();

        try {
            PendingBroadcast pending = objectMapper.readValue(json, PendingBroadcast.class);
            return Optional.of(buildResponse(
                pending.driverId(),
                pending.driverName(),
                pending.photoUrl(),
                pending.latitude(),
                pending.longitude(),
                pending.bearing(),
                pending.speed(),
                pending.accuracy(),
                pending.timestamp(),
                pending.seq()
            ));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize location for driver {}: {}", driverId, e.getMessage());
            return Optional.empty();
        }
    }

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

    public void removeLocation(UUID driverId) {
        stringRedisTemplate.delete(LOCATION_KEY_PREFIX + driverId);
        stringRedisTemplate.delete(SEQ_KEY_PREFIX + driverId);
        stringRedisTemplate.delete(LAST_UPDATE_KEY_PREFIX + driverId);
        pendingBroadcasts.remove(driverId);
    }

    public Optional<Driver> resolveDriverByEmail(String email) {
        return userLookupService.findByEmail(email)
                .flatMap(user -> driverRepository.findByUserId(user.getId()));
    }
}