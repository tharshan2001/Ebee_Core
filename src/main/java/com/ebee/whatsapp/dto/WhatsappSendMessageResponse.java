package com.ebee.whatsapp.dto;

import java.util.List;

public record WhatsappSendMessageResponse(String messagingProduct,
                                           List<WhatsappMessageId> messages,
                                           String success) {
}
