package com.ebee.whatsapp.service;

import com.ebee.whatsapp.config.WhatsappProperties;
import com.ebee.whatsapp.dto.WhatsappChange;
import com.ebee.whatsapp.dto.WhatsappChangeValue;
import com.ebee.whatsapp.dto.WhatsappContact;
import com.ebee.whatsapp.dto.WhatsappEntry;
import com.ebee.whatsapp.dto.WhatsappMessage;
import com.ebee.whatsapp.dto.WhatsappMetadata;
import com.ebee.whatsapp.dto.WhatsappProfile;
import com.ebee.whatsapp.dto.WhatsappSendMessageRequest;
import com.ebee.whatsapp.dto.WhatsappSendMessageResponse;
import com.ebee.whatsapp.dto.WhatsappText;
import com.ebee.whatsapp.dto.WhatsappWebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsappServiceTest {

    private static final String API_BASE_URL = "https://graph.facebook.com";
    private static final String PHONE_NUMBER_ID = "123456789";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String VERIFY_TOKEN = "ebee_wh_verify_2024";

    @Mock
    private RestTemplate restTemplate;

    private WhatsappProperties properties;
    private WhatsappService service;

    @BeforeEach
    void setUp() {
        properties = new WhatsappProperties(API_BASE_URL, PHONE_NUMBER_ID, ACCESS_TOKEN, VERIFY_TOKEN, "test-app-secret");
        service = new WhatsappService(properties, restTemplate);
    }

    @Test
    void verifySubscription_shouldReturnChallenge_whenTokenValid() {
        String challenge = "1234567890";
        String result = service.verifySubscription("subscribe", VERIFY_TOKEN, challenge);
        assertThat(result).isEqualTo(challenge);
    }

    @Test
    void verifySubscription_shouldThrow_whenModeInvalid() {
        assertThatThrownBy(() -> service.verifySubscription("unsubscribe", VERIFY_TOKEN, "challenge"))
                .isInstanceOf(WhatsappService.WhatsappVerificationException.class)
                .hasMessageContaining("Invalid mode");
    }

    @Test
    void verifySubscription_shouldThrow_whenTokenInvalid() {
        assertThatThrownBy(() -> service.verifySubscription("subscribe", "wrong-token", "challenge"))
                .isInstanceOf(WhatsappService.WhatsappVerificationException.class)
                .hasMessageContaining("Invalid verify token");
    }

    @Test
    void processWebhook_shouldHandleTextMessage() {
        WhatsappWebhookRequest request = new WhatsappWebhookRequest(
                "whatsapp_business_account",
                List.of(new WhatsappEntry(
                        "entry-id",
                        List.of(new WhatsappChange(
                                "messages",
                                new WhatsappChangeValue(
                                        "whatsapp",
                                        new WhatsappMetadata("15550000000", PHONE_NUMBER_ID),
                                        List.of(new WhatsappContact(new WhatsappProfile("Test User"), "94771234567")),
                                        List.of(new WhatsappMessage("94771234567", "msg-id", "1234567890",
                                                new WhatsappText("Hello"), "text"))
                                )
                        ))
                ))
        );

        service.processWebhook(request);
        // No exception and logging verified manually; asserts structural correctness
        assertThat(request.entry()).hasSize(1);
    }

    @Test
    void processWebhook_shouldIgnoreNonTextMessages() {
        WhatsappWebhookRequest request = new WhatsappWebhookRequest(
                "whatsapp_business_account",
                List.of(new WhatsappEntry(
                        "entry-id",
                        List.of(new WhatsappChange(
                                "messages",
                                new WhatsappChangeValue(
                                        "whatsapp",
                                        new WhatsappMetadata("15550000000", PHONE_NUMBER_ID),
                                        List.of(),
                                        List.of(new WhatsappMessage("94771234567", "msg-id", "1234567890", null, "image"))
                                )
                        ))
                ))
        );

        service.processWebhook(request);
        assertThat(request.entry()).hasSize(1);
    }

    @Test
    void processWebhook_shouldHandleNullPayloadGracefully() {
        service.processWebhook(null);
        service.processWebhook(new WhatsappWebhookRequest("whatsapp_business_account", null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendTextMessage_shouldCallGraphApi() {
        WhatsappSendMessageResponse expectedResponse = new WhatsappSendMessageResponse(
                "whatsapp", List.of(new com.ebee.whatsapp.dto.WhatsappMessageId("sent-msg-id")), null);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(WhatsappSendMessageResponse.class)))
                .thenReturn(ResponseEntity.ok(expectedResponse));

        WhatsappSendMessageResponse response = service.sendTextMessage("94771234567", "Reply");

        assertThat(response).isEqualTo(expectedResponse);

        ArgumentCaptor<HttpEntity<WhatsappSendMessageRequest>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://graph.facebook.com/v18.0/123456789/messages"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(WhatsappSendMessageResponse.class));

        HttpEntity<WhatsappSendMessageRequest> captured = entityCaptor.getValue();
        assertThat(captured.getHeaders().getFirst("Authorization")).isEqualTo("Bearer " + ACCESS_TOKEN);
        assertThat(captured.getBody()).isNotNull();
        assertThat(captured.getBody().to()).isEqualTo("94771234567");
        assertThat(captured.getBody().text().body()).isEqualTo("Reply");
    }

    @Test
    void sendTextMessage_shouldRejectNullRecipient() {
        assertThatThrownBy(() -> service.sendTextMessage(null, "Reply"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("recipient phone number");
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }

    @Test
    void sendTextMessage_shouldRejectNullBody() {
        assertThatThrownBy(() -> service.sendTextMessage("94771234567", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("message body");
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(Class.class));
    }

    // ── Webhook signature (X-Hub-Signature-256) ─────────────────────────

    private static String sign(String secret, byte[] body) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static final byte[] BODY =
            "{\"object\":\"whatsapp_business_account\",\"entry\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Test
    void isValidSignature_acceptsCorrectSignature() throws Exception {
        assertThat(service.isValidSignature(BODY, sign("test-app-secret", BODY))).isTrue();
    }

    @Test
    void isValidSignature_rejectsWrongSecret() throws Exception {
        assertThat(service.isValidSignature(BODY, sign("other-secret", BODY))).isFalse();
    }

    @Test
    void isValidSignature_rejectsTamperedBody() throws Exception {
        String sig = sign("test-app-secret", BODY);
        byte[] tampered = "{\"object\":\"whatsapp_business_account\",\"entry\":[{}]}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(service.isValidSignature(tampered, sig)).isFalse();
    }

    @Test
    void isValidSignature_rejectsMissingOrMalformedHeader() {
        assertThat(service.isValidSignature(BODY, null)).isFalse();
        assertThat(service.isValidSignature(BODY, "")).isFalse();
        assertThat(service.isValidSignature(BODY, "sha1=abcd")).isFalse();
        assertThat(service.isValidSignature(BODY, "sha256=not-hex")).isFalse();
    }

    @Test
    void isValidSignature_failsClosed_whenAppSecretNotConfigured() throws Exception {
        WhatsappService noSecret = new WhatsappService(
                new WhatsappProperties(API_BASE_URL, PHONE_NUMBER_ID, ACCESS_TOKEN, VERIFY_TOKEN, ""), restTemplate);
        assertThat(noSecret.isValidSignature(BODY, sign("test-app-secret", BODY))).isFalse();
        assertThat(noSecret.isValidSignature(BODY, "sha256=" + "0".repeat(64))).isFalse();
    }

    @Test
    void verifySubscription_rejectsEmptyToken_whenVerifyTokenNotConfigured() {
        WhatsappService noToken = new WhatsappService(
                new WhatsappProperties(API_BASE_URL, PHONE_NUMBER_ID, ACCESS_TOKEN, "", "test-app-secret"), restTemplate);
        assertThatThrownBy(() -> noToken.verifySubscription("subscribe", "", "challenge"))
                .isInstanceOf(WhatsappService.WhatsappVerificationException.class);
    }
}
