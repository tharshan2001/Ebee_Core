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

import java.util.Objects;

@Service
public class WhatsappService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappService.class);
    private static final String SUBSCRIBE_MODE = "subscribe";
    private static final String API_VERSION = "v18.0";

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
        if (!Objects.equals(properties.getVerifyToken(), token)) {
            log.warn("WhatsApp webhook verification failed: verify token mismatch");
            throw new WhatsappVerificationException("Invalid verify token");
        }
        log.info("WhatsApp webhook verified successfully for phoneNumberId='{}'", properties.getPhoneNumberId());
        return challenge;
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
            log.info("Received WhatsApp message from='{}' body='{}'", message.from(), body);
            // TODO: plug into order/agent workflow as needed
        }
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
