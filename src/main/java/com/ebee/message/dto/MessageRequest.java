package com.ebee.message.dto;

import com.ebee.message.enums.MessageTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Body is required")
    private String body;

    @NotNull(message = "Target type is required")
    private MessageTargetType targetType;
    private List<UUID> recipientIds;
    private List<ManualRecipientEntry> manualRecipients;
}
