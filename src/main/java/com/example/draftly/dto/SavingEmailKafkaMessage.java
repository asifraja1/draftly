package com.example.draftly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavingEmailKafkaMessage {
    private String messageId;
    private String threadId;
    private String senderEmail;
    private String subject;
    private String body;
    private String gmailAccountEmail;
    private String relationName;
    private String relationContext;
}
