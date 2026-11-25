package com.ebee.whatsapp.service;

import com.ebee.whatsapp.config.WhatsappProperties;
import com.ebee.whatsapp.dto.WhatsappChange;
import com.ebee.whatsapp.dto.WhatsappChangeValue;
import com.ebee.whatsapp.dto.WhatsappEntry;
import com.ebee.whatsapp.dto.WhatsappMessage;
import com.ebee.whatsapp.dto.WhatsappSendMessageRequest;
import com.ebee.whatsapp.dto.WhatsappSendMessageResponse;
import com.ebee.whatsapp.dto.WhatsappWebhookRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

@Service
public class WhatsappService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappService.class);
    private static final String SUBSCRIBE_MODE = "subscribe";
    private static final String API_VERSION = "v18.0";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final WhatsappProperties properties;
    private final RestTemplate restTemplate;

    public WhatsappService(WhatsappProperties properties, RestTemplate whatsappRestTemplate) {
        this.properties = properties;
        this.restTemplate = whatsappRestTemplate;
    }

    public String verifySubscription(String mode, String token, String challenge) {
        if (!SUBSCRIBE_MODE.equals(mode)) {
            log.warn("WhatsApp webhook verification failed: mode='{}' is not '{}'", mode, SUBSCRIBE_MODE);
            throw new WhatsappVerificationException("Invalid mode");
        }
        String expected = properties.getVerifyToken();
        if (expected == null || expected.isBlank()) {
            log.error("WhatsApp webhook verification refused: WHATSAPP_VERIFY_TOKEN is not configured");
            throw new WhatsappVerificationException("Verify token not configured");
        }
        if (token == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            log.warn("WhatsApp webhook verification failed: verify token mismatch");
            throw new WhatsappVerificationException("Invalid verify token");
        }
        log.info("WhatsApp webhook verified successfully for phoneNumberId='{}'", properties.getPhoneNumberId());
        return challenge;
    }

    /**
     * Checks Meta's {@code X-Hub-Signature-256} header: an HMAC-SHA256 of the raw request
     * body keyed with the app secret. Fails closed when the secret is not configured.
     */
    public boolean isValidSignature(byte[] rawBody, String signatureHeader) {
        String appSecret = properties.getAppSecret();
        if (appSecret == null || appSecret.isBlank()) {
            log.error("Rejecting WhatsApp webhook: WHATSAPP_APP_SECRET is not configured");
            return false;
        }
        if (rawBody == null || signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(signatureHeader.substring(SIGNATURE_PREFIX.length()).trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(rawBody), provided);
        } catch (GeneralSecurityException e) {
            log.error("HMAC-SHA256 unavailable for WhatsApp signature check", e);
            return false;
        }
    }

    public void processWebhook(WhatsappWebhookRequest request) {
        if (request == null || request.entry() == null) {
            log.debug("Received empty WhatsApp webhook payload");
            return;
        }

        for (WhatsappEntry entry : request.entry()) {
            if (entry.changes() == null) {
                continue;
            }
            for (WhatsappChange change : entry.changes()) {
                handleChange(change);
            }
        }
    }

    private void handleChange(WhatsappChange change) {
        WhatsappChangeValue value = change.value();
        if (value == null || value.messages() == null) {
            return;
        }

        for (WhatsappMessage message : value.messages()) {
            if (!"text".equals(message.type())) {
                continue;
            }
            String body = message.text() != null ? message.text().body() : null;
            // Don't log message content or full phone numbers: customer data.
            log.info("Received WhatsApp text message id='{}' from='{}' length={}",
                    message.id(), maskPhone(message.from()), body != null ? body.length() : 0);
            // TODO: plug into order/agent workflow as needed
        }
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }

    public WhatsappSendMessageResponse sendTextMessage(String to, String body) {
        Objects.requireNonNull(to, "recipient phone number must not be null");
        Objects.requireNonNull(body, "message body must not be null");

        String url = "%s/%s/%s/messages".formatted(properties.getApiBaseUrl(), API_VERSION, properties.getPhoneNumberId());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        WhatsappSendMessageRequest request = WhatsappSendMessageRequest.text(to, body);
        HttpEntity<WhatsappSendMessageRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<WhatsappSendMessageResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, WhatsappSendMessageResponse.class);

        log.info("WhatsApp message sent to='{}' responseStatus='{}'", to, response.getStatusCode());
        return response.getBody();
    }

    public static class WhatsappVerificationException extends RuntimeException {
        public WhatsappVerificationException(String message) {
            super(message);
        }
    }
}
