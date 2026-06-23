package com.ebee.whatsapp.dto;

public record WhatsappMessage(String from, String id, String timestamp,
                               WhatsappText text, String type) {
}
