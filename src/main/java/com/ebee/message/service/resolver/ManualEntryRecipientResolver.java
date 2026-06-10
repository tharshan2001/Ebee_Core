package com.ebee.message.service.resolver;

import com.ebee.message.dto.ManualRecipientEntry;
import com.ebee.message.entity.Message;
import com.ebee.message.entity.MessageRecipient;
import com.ebee.message.enums.MessageTargetType;
import com.ebee.message.enums.RecipientType;
import com.ebee.message.service.RecipientResolutionContext;
import com.ebee.message.service.RecipientResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ManualEntryRecipientResolver implements RecipientResolver {

    @Override
    public MessageTargetType targetType() {
        return MessageTargetType.MANUAL_ENTRY;
    }

    @Override
    public List<MessageRecipient> resolve(Message message, RecipientResolutionContext context) {
        if (context.manualRecipients() == null || context.manualRecipients().isEmpty()) {
            throw new IllegalArgumentException("manualRecipients required for MANUAL_ENTRY");
        }
        return context.manualRecipients().stream()
                .map(entry -> buildRecipient(message, entry))
                .toList();
    }

    private MessageRecipient buildRecipient(Message message, ManualRecipientEntry entry) {
        return MessageRecipient.builder()
                .message(message)
                .recipientType(RecipientType.MANUAL)
                .recipientId(UUID.randomUUID())
                .name(entry.getName())
                .phoneNumber(entry.getPhoneNumber())
                .email(entry.getEmail())
                .build();
    }
}
