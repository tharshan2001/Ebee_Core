package com.ebee.message.service;

import com.ebee.message.dto.ManualRecipientEntry;
import com.ebee.message.dto.MessageRequest;

import java.util.List;
import java.util.UUID;

public record RecipientResolutionContext(List<UUID> recipientIds, List<ManualRecipientEntry> manualRecipients) {

    public static RecipientResolutionContext from(MessageRequest request) {
        return new RecipientResolutionContext(request.getRecipientIds(), request.getManualRecipients());
    }
}
