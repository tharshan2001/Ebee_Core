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
public class EmailRequest {
    private String to;
    private String subject;
    private String body;
    private boolean html;
    private List<String> cc;
    private List<String> bcc;
}
