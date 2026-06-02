package com.ebee.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsRequest {
    private List<String> phoneNumbers;
    private String message;
    private String mask;
    private String campaignName;
}
