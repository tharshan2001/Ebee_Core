package com.ebee.driver.websocket;

import com.ebee.auth.service.JwtService;
import com.ebee.common.config.AppProperties;
import com.ebee.driver.dto.DriverLocationRequest;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.driver.service.DriverLocationService;
import com.ebee.user.enums.RoleType;
import com.ebee.user.service.UserLookupService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverLocationWebSocketHandler extends TextWebSocketHandler {

    private final JwtService jwtService;
    private final UserLookupService userLookupService;
    private final DriverRepository driverRepository;
    private final DriverLocationService driverLocationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    record SessionInfo(String email, UUID driverId) {}

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Raw WS connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage raw) {
        String payload = raw.getPayload();
        SessionInfo info = sessions.get(session.getId());

        if (info == null) {
            if (!authenticate(session, payload)) return;
            return;
        }

        if ("PING".equals(payload)) {
            send(session, "PONG");
            return;
        }

        if ("LOCATION".equals(payload)) return;

        try {
            DriverLocationRequest request = objectMapper.readValue(payload, DriverLocationRequest.class);
            var response = driverLocationService.updateLocation(info.driverId(), info.email(), request);
            messagingTemplate.convertAndSend("/topic/driver-locations", response);
            log.debug("Location broadcast for driver {}: ({}, {})",
                info.driverId(), request.latitude(), request.longitude());
        } catch (Exception e) {
            log.warn("Bad location from driver {}: {}", info.driverId(), e.getMessage());
            send(session, "ERROR:Invalid location JSON");
        }
    }

    private boolean authenticate(WebSocketSession session, String token) {
        try {
            String email = jwtService.extractEmail(token);
            RoleType role = jwtService.extractRole(token);
            if (role != RoleType.DRIVER) {
                log.warn("Raw WS auth rejected: not a driver, role={}", role);
                send(session, "ERROR:Forbidden");
                session.close(CloseStatus.POLICY_VIOLATION);
                return false;
            }

            Optional<Driver> driverOpt = userLookupService.findByEmail(email)
                .flatMap(user -> driverRepository.findByUserId(user.getId()));

            if (driverOpt.isEmpty()) {
                log.warn("Raw WS auth rejected: no driver record for {}", email);
                send(session, "ERROR:No driver record");
                session.close(CloseStatus.POLICY_VIOLATION);
                return false;
            }

            UUID driverId = driverOpt.get().getId();
            sessions.put(session.getId(), new SessionInfo(email, driverId));
            send(session, "AUTH_OK");
            sendGpsConfig(session);
            log.info("Raw WS auth OK: session={} driverId={}", session.getId(), driverId);
            return true;
        } catch (Exception e) {
            log.warn("Raw WS auth failed: {}", e.getMessage());
            send(session, "ERROR:Auth failed");
            return false;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("Raw WS closed: {} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable error) {
        sessions.remove(session.getId());
        log.error("Raw WS error: {} error={}", session.getId(), error.getMessage());
    }

    private void send(WebSocketSession session, String text) {
        try {
            session.sendMessage(new TextMessage(text));
        } catch (Exception e) {
            log.warn("WS send failed: {}", e.getMessage());
        }
    }

    private void sendGpsConfig(WebSocketSession session) {
        var driver = appProperties.getDriver();
        String config = String.format(
            "{\"type\":\"GPS_CONFIG\",\"gpsIntervalMs\":%d,\"minAccuracy\":%d}",
            driver.getGpsIntervalMs(),
            driver.getMinAccuracy()
        );
        send(session, config);
        log.info("Sent GPS config to session {}: gpsIntervalMs={}, minAccuracy={}",
            session.getId(), driver.getGpsIntervalMs(), driver.getMinAccuracy());
    }
}
