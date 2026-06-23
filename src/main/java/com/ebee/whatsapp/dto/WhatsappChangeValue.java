package com.ebee.whatsapp.dto;

import java.util.List;

public record WhatsappChangeValue(String messagingProduct, WhatsappMetadata metadata,
                                   List<WhatsappContact> contacts, List<WhatsappMessage> messages) {
}
