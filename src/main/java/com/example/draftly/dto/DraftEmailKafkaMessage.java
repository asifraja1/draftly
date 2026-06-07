package com.example.draftly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftEmailKafkaMessage {
    private String senderEmail;
    private String threadId;
    private String gmailAccountEmail;
    private String relationName;
    private String relationContext;
    private String emailBody;
    private String userDecision;   // set when re-submitted after the user answered a decision request
}
