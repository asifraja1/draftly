package com.example.draftly.service;

import com.example.draftly.dto.EmailProcessResponse;
import com.example.draftly.dto.SavingEmailKafkaMessage;
import com.example.draftly.entity.GmailAccount;
import com.example.draftly.entity.PendingRelationEmail;
import com.example.draftly.entity.Relation;
import com.example.draftly.config.GmailConfig;
import com.example.draftly.repository.GmailAccountRepository;
import com.example.draftly.repository.PendingRelationEmailRepository;
import com.example.draftly.repository.RelationRepository;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmailBatchProcessService {

    private final GmailAccountRepository gmailAccountRepository;
    private final RelationRepository relationRepository;
    private final PendingRelationEmailRepository pendingRepo;
    private final KafkaProducerService kafkaProducerService;
    private final EmailSaveService emailSaveService;
    private final GmailConfig gmailConfig;

    public EmailProcessResponse processLast50Emails(OAuth2AuthenticationToken authentication) throws Exception {
        OAuth2User oauthUser = authentication.getPrincipal();
        String accountEmail = oauthUser.getAttribute("email");

        GmailAccount gmailAccount = gmailAccountRepository
                .findByGmailAddress(accountEmail)
                .orElseThrow(() -> new RuntimeException("Gmail account not found"));

        Gmail gmail = gmailConfig.getGmailService(gmailAccount);

        // Only the user's OWN SENT emails — we train the model on the user's
        // writing style, not on what other people sent them.
        ListMessagesResponse response = gmail.users()
                .messages()
                .list("me")
                .setQ("in:sent")
                .setMaxResults(50L)
                .execute();

        if (response.getMessages() == null) {
            return EmailProcessResponse.builder()
                    .totalFetched(0).publishedToKafka(0).pendingRelations(0)
                    .pendingEmails(List.of())
                    .build();
        }

        int published = 0;
        List<EmailProcessResponse.PendingEmailInfo> pendingList = new ArrayList<>();

        for (Message ref : response.getMessages()) {
            Message full = gmail.users().messages().get("me", ref.getId()).execute();

            // persist email locally first
            emailSaveService.saveEmail(full, gmailAccount);

            // For a SENT email, the contact is the recipient (the "To" header),
            // NOT the sender (which is the user themselves).
            String recipientHeader = extractHeader(full, "To");
            String subject = extractHeader(full, "Subject");
            String recipientEmail = extractEmailAddress(recipientHeader);

            // Skip emails the user sent to themselves
            if (recipientEmail.isEmpty() || recipientEmail.equalsIgnoreCase(accountEmail)) {
                continue;
            }

            Optional<Relation> relation = relationRepository
                    .findByGmailAccountEmailAndEmailAddressIgnoreCase(accountEmail, recipientEmail);

            if (relation.isPresent()) {
                // Train on the USER'S OWN sent email body (their writing style)
                kafkaProducerService.publishToSavingEmail(
                        SavingEmailKafkaMessage.builder()
                                .messageId(full.getId())
                                .threadId(full.getThreadId())
                                .senderEmail(recipientEmail)   // the contact this style is for
                                .subject(subject)
                                .body(full.getSnippet())
                                .gmailAccountEmail(accountEmail)
                                .relationName(relation.get().getRelationName())
                                .relationContext(relation.get().getContext())
                                .build()
                );
                published++;
            } else {
                // no relation for this recipient — store as pending, ask user
                boolean alreadyPending = pendingRepo
                        .findByGmailAccount_GmailAddressAndSenderEmail(accountEmail, recipientEmail)
                        .stream()
                        .anyMatch(p -> p.getMessageId().equals(full.getId()));

                if (!alreadyPending) {
                    pendingRepo.save(PendingRelationEmail.builder()
                            .senderEmail(recipientEmail)
                            .threadId(full.getThreadId())
                            .messageId(full.getId())
                            .source("BATCH")
                            .gmailAccount(gmailAccount)
                            .build());
                }

                pendingList.add(EmailProcessResponse.PendingEmailInfo.builder()
                        .messageId(full.getId())
                        .threadId(full.getThreadId())
                        .senderEmail(recipientEmail)
                        .subject(subject)
                        .build());
            }
        }

        System.out.println("=================================");
        System.out.println("BATCH PROCESS DONE");
        System.out.println("Total    : " + response.getMessages().size());
        System.out.println("Published: " + published);
        System.out.println("Pending  : " + pendingList.size());
        System.out.println("=================================");

        return EmailProcessResponse.builder()
                .totalFetched(response.getMessages().size())
                .publishedToKafka(published)
                .pendingRelations(pendingList.size())
                .pendingEmails(pendingList)
                .build();
    }

    /**
     * Fetch the user's sent emails to a specific contact and train on them.
     * Called when a relation is created/updated, so the model learns how the
     * user writes to THAT person from their full sent history with them.
     *
     * Runs async so it never blocks the relation-save request.
     */
    @org.springframework.scheduling.annotation.Async
    public void trainOnSentEmailsTo(String accountEmail, String contactEmail, Relation relation) {
        try {
            GmailAccount gmailAccount = gmailAccountRepository
                    .findByGmailAddress(accountEmail)
                    .orElse(null);
            if (gmailAccount == null) return;

            Gmail gmail = gmailConfig.getGmailService(gmailAccount);

            // Gmail server-side filter: only emails the user SENT to this contact
            ListMessagesResponse response = gmail.users()
                    .messages()
                    .list("me")
                    .setQ("in:sent to:" + contactEmail)
                    .setMaxResults(30L)
                    .execute();

            if (response.getMessages() == null) {
                System.out.println("[TRAIN] No sent emails found to " + contactEmail);
                return;
            }

            int trained = 0;
            for (Message ref : response.getMessages()) {
                Message full = gmail.users().messages().get("me", ref.getId()).execute();
                emailSaveService.saveEmail(full, gmailAccount);

                kafkaProducerService.publishToSavingEmail(
                        SavingEmailKafkaMessage.builder()
                                .messageId(full.getId())
                                .threadId(full.getThreadId())
                                .senderEmail(contactEmail)
                                .subject(extractHeader(full, "Subject"))
                                .body(full.getSnippet())
                                .gmailAccountEmail(accountEmail)
                                .relationName(relation.getRelationName())
                                .relationContext(relation.getContext())
                                .build()
                );
                trained++;
            }
            System.out.println("[TRAIN] Trained on " + trained + " sent email(s) to " + contactEmail);
        } catch (Exception e) {
            System.err.println("[TRAIN] Failed to train on sent emails to " + contactEmail + ": " + e.getMessage());
        }
    }

    private String extractHeader(Message message, String name) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null) return "";
        for (MessagePartHeader h : message.getPayload().getHeaders()) {
            if (name.equalsIgnoreCase(h.getName())) return h.getValue();
        }
        return "";
    }

    // "John Doe <John@Example.com>, other@x.com" → "john@example.com"
    // Takes the FIRST recipient and normalizes to lowercase.
    private String extractEmailAddress(String header) {
        if (header == null || header.isBlank()) return "";
        // A "To" header may list several recipients — use the first.
        String first = header.split(",")[0].trim();
        int start = first.indexOf('<');
        int end = first.indexOf('>');
        if (start >= 0 && end > start) return first.substring(start + 1, end).trim().toLowerCase();
        return first.trim().toLowerCase();
    }
}
