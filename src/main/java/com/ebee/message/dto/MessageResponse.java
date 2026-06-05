package com.ebee.message.dto;

import com.ebee.message.enums.MessageTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {
    private UUID id;
    private String subject;
    private String body;
    private MessageTargetType targetType;
    private Integer totalRecipients;
    private LocalDateTime sentAt;
    private List<MessageRecipientResponse> recipients;
}
