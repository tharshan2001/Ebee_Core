package com.ebee.notification.sender.sms;

import com.ebee.common.util.PhoneUtils;
import com.ebee.notification.config.NotificationProperties;
import com.ebee.notification.model.NotificationResult;
import com.ebee.notification.model.SmsRequest;
import com.ebee.notification.sender.SmsSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class RichCommunicationSmsSender implements SmsSender {

    private final NotificationProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public NotificationResult send(SmsRequest request) {
        try {
            String digest = md5(properties.getSms().getPassword());
            String created = ZonedDateTime.now(ZoneId.of("Asia/Colombo"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("USER", properties.getSms().getUsername());
            headers.set("DIGEST", digest);
            headers.set("CREATED", created);
            String authString = properties.getSms().getAuthString();
            if (authString != null && !authString.isBlank()) {
                headers.set("API-AUTH-STRING", authString);
            }

            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode messages = root.putArray("messages");

            String mask = request.getMask() != null ? request.getMask() : properties.getSms().getDefaultMask();
            String campaign = request.getCampaignName() != null ? request.getCampaignName() : properties.getSms().getDefaultCampaign();

            for (String phone : request.getPhoneNumbers()) {
                ObjectNode msg = messages.addObject();
                msg.put("number", normalizePhone(phone));
                if (mask != null && !mask.isBlank()) {
                    msg.put("mask", mask);
                }
                msg.put("text", request.getMessage());
                msg.put("campaignName", campaign);
            }

            String url = properties.getSms().getApiBaseUrl() + "/sms/send";
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(root), headers);

            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);
            JsonNode body = response.getBody();

            log.debug("Dialog API response: {}", body);

            boolean allMessagesOk = body != null && body.has("messages") && body.get("messages").isArray()
                    && StreamSupport.stream(body.get("messages").spliterator(), false)
                            .allMatch(m -> m.has("resultCode") && m.get("resultCode").asInt() == 0);

            if (allMessagesOk) {
                String serverRef = body.get("messages").get(0).path("serverRef").asText(null);

                log.info("SMS sent successfully to: {}", request.getPhoneNumbers());
                return NotificationResult.builder()
                        .success(true)
                        .message("SMS sent successfully")
                        .serverRef(serverRef)
                        .channel("SMS")
                        .build();
            }

            String desc = body != null ? body.path("resultDesc").asText("Unknown error") : "No response";
            log.warn("SMS API returned error: {}", desc);
            return NotificationResult.builder()
                    .success(false)
                    .message("SMS failed: " + desc)
                    .channel("SMS")
                    .build();

        } catch (Exception e) {
            log.error("Failed to send SMS to: {}", request.getPhoneNumbers(), e);
            return NotificationResult.builder()
                    .success(false)
                    .message("SMS failed: " + e.getMessage())
                    .channel("SMS")
                    .build();
        }
    }

    private String normalizePhone(String phone) {
        return PhoneUtils.forSms(phone);
    }

    private String md5(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
}
