package com.ebee.whatsapp.dto;

import java.util.List;

public record WhatsappEntry(String id, List<WhatsappChange> changes) {
}
