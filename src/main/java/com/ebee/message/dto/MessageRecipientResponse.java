package com.ebee.message.dto;

import com.ebee.message.enums.RecipientType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRecipientResponse {
    private UUID id;
    private RecipientType recipientType;
    private UUID recipientId;
    private String name;
    private String phoneNumber;
    private String email;
    private Boolean sentSms;
    private Boolean sentEmail;
}
