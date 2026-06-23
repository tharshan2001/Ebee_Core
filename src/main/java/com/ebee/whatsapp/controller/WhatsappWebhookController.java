package com.ebee.whatsapp.controller;

import com.ebee.whatsapp.dto.WhatsappWebhookRequest;
import com.ebee.whatsapp.service.WhatsappService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/whatsapp/webhook")
public class WhatsappWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsappWebhookController.class);

    private final WhatsappService whatsappService;

    public WhatsappWebhookController(WhatsappService whatsappService) {
        this.whatsappService = whatsappService;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {
        try {
            String response = whatsappService.verifySubscription(mode, verifyToken, challenge);
            return ResponseEntity.ok(response);
        } catch (WhatsappService.WhatsappVerificationException ex) {
            log.warn("WhatsApp webhook verification rejected: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(@RequestBody WhatsappWebhookRequest request) {
        whatsappService.processWebhook(request);
        return ResponseEntity.ok().build();
    }
}
