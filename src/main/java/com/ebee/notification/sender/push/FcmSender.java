package com.ebee.notification.sender.push;

import com.ebee.notification.model.PushRequest;
import com.ebee.notification.sender.PushSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmSender implements PushSender {

    private static final String FCM_SEND_URL = "https://fcm.googleapis.com/v1/projects/%s/messages:send";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final long TOKEN_BUFFER_SECONDS = 300;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final FcmProperties fcmProperties;

    private String serviceAccountJson;
    private volatile AccessToken cachedToken;
    private final ReentrantLock tokenLock = new ReentrantLock();

    @PostConstruct
    void init() throws IOException {
        if (fcmProperties.getConfigPath() != null && !fcmProperties.getConfigPath().isBlank()) {
            serviceAccountJson = Files.readString(Paths.get(fcmProperties.getConfigPath()));
            log.info("Loaded Firebase service account from {}", fcmProperties.getConfigPath());
        } else {
            log.warn("FCM push enabled but notification.push.config-path is not set");
        }
    }

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    @Override
    public void send(PushRequest request) {
        if (!fcmProperties.isEnabled()) {
            log.warn("Push notifications disabled — skipping");
            return;
        }
        if (request.token() != null && request.token().startsWith("ExponentPushToken[")) {
            sendExpoPush(request);
            return;
        }
        sendFcmPush(request);
    }

    private void sendExpoPush(PushRequest request) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("to", request.token());
            body.put("title", request.title());
            body.put("body", request.body());
            if (request.data() != null && !request.data().isEmpty()) {
                body.put("data", objectMapper.valueToTree(request.data()));
            }
            body.put("sound", "default");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    EXPO_PUSH_URL, HttpMethod.POST, entity, JsonNode.class
            );

            if (response.getBody() != null) {
                JsonNode data = response.getBody().path("data");
                JsonNode result;
                if (data.isArray()) {
                    result = data.get(0);
                } else if (data.isObject() && !data.isMissingNode()) {
                    result = data;
                } else {
                    log.warn("Expo push response has no data field: {}", response.getBody());
                    return;
                }
                if (result == null) {
                    log.warn("Expo push response data is empty: {}", response.getBody());
                    return;
                }
                String status = result.path("status").asText();
                if ("ok".equals(status)) {
                    log.info("Expo push sent OK: id={}, token={}", result.path("id").asText(), maskToken(request.token()));
                } else {
                    log.warn("Expo push failed: {} — {} | response: {}", status, result.path("message").asText(), response.getBody());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to send Expo push to {}: {}", maskToken(request.token()), e.getMessage());
        }
    }

    private void sendFcmPush(PushRequest request) {
        log.info("FCM push start: token={}", maskToken(request.token()));
        String accessToken = getAccessToken();
        if (accessToken == null) {
            log.warn("Cannot send FCM push — no access token available");
            return;
        }

        ObjectNode message = objectMapper.createObjectNode();
        ObjectNode inner = message.putObject("message");
        inner.put("token", request.token());

        ObjectNode notification = inner.putObject("notification");
        notification.put("title", request.title());
        notification.put("body", request.body());

        if (request.data() != null && !request.data().isEmpty()) {
            ObjectNode data = inner.putObject("data");
            request.data().forEach(data::put);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(message), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    FCM_SEND_URL.formatted(fcmProperties.getProjectId()),
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("FCM push sent OK to token={}: {}", maskToken(request.token()), response.getBody());
            } else {
                JsonNode error = objectMapper.readTree(response.getBody());
                String status = error.path("error").path("status").asText();
                log.warn("FCM send failed: {} — {}", status, response.getBody());
                if ("UNREGISTERED".equals(status) || "INVALID_ARGUMENT".equals(status)) {
                    log.info("Stale/invalid FCM token, should be cleared: {}", maskToken(request.token()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to send FCM push to {}: {}", maskToken(request.token()), e.getMessage());
        }
    }

    private String getAccessToken() {
        AccessToken token = cachedToken;
        if (token != null && !token.isExpired()) {
            return token.value();
        }
        tokenLock.lock();
        try {
            token = cachedToken;
            if (token != null && !token.isExpired()) {
                return token.value();
            }
            String newToken = fetchAccessToken();
            if (newToken != null) {
                cachedToken = new AccessToken(newToken, Instant.now().getEpochSecond());
            }
            return newToken;
        } finally {
            tokenLock.unlock();
        }
    }

    private String fetchAccessToken() {
        try {
            if (serviceAccountJson == null) {
                log.warn("Firebase service account not loaded");
                return null;
            }
            JsonNode serviceAccount = objectMapper.readTree(
                    new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8))
            );
            String clientEmail = serviceAccount.get("client_email").asText();
            String privateKeyPem = serviceAccount.get("private_key").asText();
            String projectId = serviceAccount.get("project_id").asText();
            if (fcmProperties.getProjectId() == null || fcmProperties.getProjectId().isBlank()) {
                fcmProperties.setProjectId(projectId);
            }

            PrivateKey privateKey = parsePrivateKey(privateKeyPem);
            long now = Instant.now().getEpochSecond();
            long exp = now + 3600;

            String jwt = createJwt(clientEmail, now, exp, privateKey);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
            body.add("assertion", jwt);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    TOKEN_URL, HttpMethod.POST, request, JsonNode.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody().get("access_token").asText();
            }
            log.warn("Failed to obtain FCM access token: {}", response.getBody());
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch FCM access token", e);
            return null;
        }
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String base64 = pem
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private String createJwt(String clientEmail, long iat, long exp, PrivateKey privateKey) throws Exception {
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
        String payload = "{\"iss\":\"%s\",\"scope\":\"%s\",\"aud\":\"%s\",\"exp\":%d,\"iat\":%d}"
                .formatted(clientEmail, SCOPE, TOKEN_URL, exp, iat);

        String b64Header = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String b64Payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        String toSign = b64Header + "." + b64Payload;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(toSign.getBytes(StandardCharsets.UTF_8));
        byte[] sigBytes = signature.sign();
        String b64Signature = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);

        return toSign + "." + b64Signature;
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 10) return "***";
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }

    private record AccessToken(String value, long fetchedAt) {
        boolean isExpired() {
            return Instant.now().getEpochSecond() >= fetchedAt + 3600 - TOKEN_BUFFER_SECONDS;
        }
    }
}
