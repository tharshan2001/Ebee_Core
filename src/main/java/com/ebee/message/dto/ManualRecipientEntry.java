package com.ebee.message.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualRecipientEntry {
    @NotBlank(message = "phoneNumber is required for manual entry")
    private String phoneNumber;

    private String name;

    private String email;
}
