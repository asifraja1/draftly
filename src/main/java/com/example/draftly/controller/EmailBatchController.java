package com.example.draftly.controller;

import com.example.draftly.dto.EmailProcessResponse;
import com.example.draftly.service.EmailBatchProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
public class EmailBatchController {

    private final EmailBatchProcessService emailBatchProcessService;

    /**
     * Fetches the last 100 emails.
     * - If sender has a known relation → publishes to saving-email Kafka topic (async).
     * - If no relation → returns those emails in `pendingEmails` list.
     *   User should then call POST /api/relations to supply the relation,
     *   which auto-flushes all pending emails to Kafka.
     */
    @PostMapping("/process")
    public ResponseEntity<EmailProcessResponse> processEmails(OAuth2AuthenticationToken authentication) {
        try {
            EmailProcessResponse result = emailBatchProcessService.processLast50Emails(authentication);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
