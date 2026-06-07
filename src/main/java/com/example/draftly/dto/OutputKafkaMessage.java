package com.example.draftly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutputKafkaMessage {

    // SUMMARY, DRAFT, or NEEDS_INPUT
    private String type;

    private String threadId;
    private String senderEmail;
    private String gmailAccountEmail;
    private String content;        // summary text OR draft email body
    private String originalEmailBody;

    // Only for NEEDS_INPUT
    private String question;
    private String options;        // JSON array string, e.g. ["Accept","Decline"]
}
