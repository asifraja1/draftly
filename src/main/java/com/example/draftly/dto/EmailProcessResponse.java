package com.example.draftly.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EmailProcessResponse {
    private int totalFetched;
    private int publishedToKafka;
    private int pendingRelations;
    private List<PendingEmailInfo> pendingEmails;

    @Data
    @Builder
    public static class PendingEmailInfo {
        private String messageId;
        private String threadId;
        private String senderEmail;
        private String subject;
    }
}
