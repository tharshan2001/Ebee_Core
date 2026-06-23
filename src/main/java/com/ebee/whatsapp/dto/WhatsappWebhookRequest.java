package com.ebee.whatsapp.dto;

import java.util.List;

public record WhatsappWebhookRequest(String object, List<WhatsappEntry> entry) {
}
