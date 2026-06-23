package com.ebee.whatsapp.dto;

public record WhatsappSendMessageRequest(String messagingProduct, String recipientType,
                                          String to, String type, WhatsappText text) {

    public static WhatsappSendMessageRequest text(String to, String body) {
        return new WhatsappSendMessageRequest("whatsapp", "individual", to, "text", new WhatsappText(body));
    }
}
