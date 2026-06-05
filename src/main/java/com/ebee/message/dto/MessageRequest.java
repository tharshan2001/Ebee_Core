package com.ebee.message.dto;

import com.ebee.message.enums.MessageTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRequest {
    private String subject;
    private String body;
    private MessageTargetType targetType;
    private List<UUID> recipientIds;
    private List<ManualRecipientEntry> manualRecipients;
}
