package com.example.draftly.service;

import com.example.draftly.config.GmailConfig;
import com.example.draftly.dto.DraftEmailKafkaMessage;
import com.example.draftly.dto.GmailNotification;
import com.example.draftly.dto.SavingEmailKafkaMessage;
import com.example.draftly.entity.GmailAccount;
import com.example.draftly.entity.PendingRelationEmail;
import com.example.draftly.entity.Relation;
import com.example.draftly.repository.GmailAccountRepository;
import com.example.draftly.repository.PendingRelationEmailRepository;
import com.example.draftly.repository.RelationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class GmailHistoryService {

    private final GmailAccountRepository gmailAccountRepository;
    private final GmailConfig gmailConfig;
    private final EmailSaveService emailSaveService;
    private final ObjectMapper objectMapper;
    private final RelationRepository relationRepository;
    private final PendingRelationEmailRepository pendingRepo;
    private final KafkaProducerService kafkaProducerService;

    public void processNotification(String base64Data) throws Exception {
        String json = new String(Base64.getDecoder().decode(base64Data));
        GmailNotification notification = objectMapper.readValue(json, GmailNotification.class);

        System.out.println();
        System.out.println("WEBHOOK RECEIVED");
        System.out.println("EMAIL      : " + notification.getEmailAddress());
        System.out.println("HISTORY ID : " + notification.getHistoryId());
        System.out.println();

        GmailAccount gmailAccount = gmailAccountRepository
                .findByGmailAddress(notification.getEmailAddress())
                .orElseThrow();

        Gmail gmail = gmailConfig.getGmailService(gmailAccount);

        ListHistoryResponse response = gmail.users()
                .history()
                .list("me")
                .setStartHistoryId(gmailAccount.getHistoryId())
                .execute();

        List<History> histories = response.getHistory();

        if (histories == null) {
            gmailAccount.setHistoryId(notification.getHistoryId());
            gmailAccountRepository.save(gmailAccount);
            return;
        }

        for (History history : histories) {
            if (history.getMessagesAdded() == null) continue;

            for (HistoryMessageAdded added : history.getMessagesAdded()) {
                Message stub = added.getMessage();
                String messageId = stub.getId();

                Message gmailMessage;
                try {
                    gmailMessage = gmail.users().messages().get("me", messageId).execute();
                } catch (Exception ex) {
                    // Message may have been deleted/moved between history events — skip it,
                    // but DON'T abort: we must still advance the history pointer below.
                    System.out.println("[WEBHOOK] Skipping message " + messageId + " — " + ex.getMessage());
                    continue;
                }

                List<String> labels = gmailMessage.getLabelIds();
                boolean isSent  = labels != null && labels.contains("SENT");
                boolean isInbox = labels != null && labels.contains("INBOX");
                boolean isDraftOrChat = labels != null
                        && (labels.contains("DRAFT") || labels.contains("CHAT"));
                if (isDraftOrChat) continue;

                try {
                    emailSaveService.saveEmail(gmailMessage, gmailAccount);

                    if (isSent) {
                        // User sent an email (from Gmail directly) → train on it
                        trainOnSentEmail(gmailMessage, gmailAccount);
                    } else if (isInbox) {
                        // Incoming email → relation check → draft-email topic
                        checkRelationAndPublish(gmailMessage, gmailAccount);
                    }
                } catch (Exception ex) {
                    System.out.println("[WEBHOOK] Error processing " + messageId + " — " + ex.getMessage());
                }
            }
        }

        // Always advance the history pointer so we don't reprocess the same window forever.
        gmailAccount.setHistoryId(notification.getHistoryId());
        gmailAccountRepository.save(gmailAccount);

        System.out.println();
        System.out.println("HISTORY UPDATED TO : " + notification.getHistoryId());
        System.out.println();
    }

    /**
     * Continuous learning: when the user sends an email (from Gmail directly),
     * train on it under the recipient's relation.
     */
    @Async
    public CompletableFuture<Void> trainOnSentEmail(Message gmailMessage, GmailAccount gmailAccount) {
        String recipientEmail = extractEmailAddress(extractHeader(gmailMessage, "To"));
        if (recipientEmail.isEmpty()
                || recipientEmail.equalsIgnoreCase(gmailAccount.getGmailAddress())) {
            return CompletableFuture.completedFuture(null);
        }

        Optional<Relation> relation = relationRepository
                .findByGmailAccountEmailAndEmailAddressIgnoreCase(gmailAccount.getGmailAddress(), recipientEmail);
        if (relation.isEmpty()) {
            // No relation for this recipient yet — nothing to train under.
            return CompletableFuture.completedFuture(null);
        }

        kafkaProducerService.publishToSavingEmail(
                SavingEmailKafkaMessage.builder()
                        .messageId(gmailMessage.getId())
                        .threadId(gmailMessage.getThreadId())
                        .senderEmail(recipientEmail)
                        .subject(extractHeader(gmailMessage, "Subject"))
                        .body(gmailMessage.getSnippet())
                        .gmailAccountEmail(gmailAccount.getGmailAddress())
                        .relationName(relation.get().getRelationName())
                        .relationContext(relation.get().getContext())
                        .build()
        );
        System.out.println("[WEBHOOK] Trained on sent email to " + recipientEmail);
        return CompletableFuture.completedFuture(null);
    }

    @Async
    public CompletableFuture<Void> checkRelationAndPublish(Message gmailMessage, GmailAccount gmailAccount) {
        String sender = extractHeader(gmailMessage, "From");
        String senderEmail = extractEmailAddress(sender);
        String threadId = gmailMessage.getThreadId();
        String body = gmailMessage.getSnippet();

        Optional<Relation> relation = relationRepository
                .findByGmailAccountEmailAndEmailAddressIgnoreCase(gmailAccount.getGmailAddress(), senderEmail);

        if (relation.isPresent()) {
            kafkaProducerService.publishToDraftEmail(
                    DraftEmailKafkaMessage.builder()
                            .senderEmail(senderEmail)
                            .threadId(threadId)
                            .gmailAccountEmail(gmailAccount.getGmailAddress())
                            .relationName(relation.get().getRelationName())
                            .relationContext(relation.get().getContext())
                            .emailBody(body)
                            .build()
            );
            System.out.println("[WEBHOOK] Relation found for " + senderEmail + " → published to draft-email");
        } else {
            // no relation — store as pending; user must add relation via POST /api/relations
            boolean alreadyPending = pendingRepo.findBySenderEmail(senderEmail)
                    .stream()
                    .anyMatch(p -> p.getMessageId().equals(gmailMessage.getId()));

            if (!alreadyPending) {
                pendingRepo.save(PendingRelationEmail.builder()
                        .senderEmail(senderEmail)
                        .threadId(threadId)
                        .messageId(gmailMessage.getId())
                        .source("WEBHOOK")
                        .gmailAccount(gmailAccount)
                        .build());
            }
            System.out.println("[WEBHOOK] No relation for " + senderEmail + " → stored as pending");
        }

        return CompletableFuture.completedFuture(null);
    }

    private String extractHeader(Message message, String name) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null) return "";
        for (var h : message.getPayload().getHeaders()) {
            if (name.equalsIgnoreCase(h.getName())) return h.getValue();
        }
        return "";
    }

    private String extractEmailAddress(String header) {
        if (header == null || header.isBlank()) return "";
        // "To" may list several recipients — use the first.
        String first = header.split(",")[0].trim();
        int start = first.indexOf('<');
        int end = first.indexOf('>');
        if (start >= 0 && end > start) return first.substring(start + 1, end).trim().toLowerCase();
        return first.trim().toLowerCase();
    }
}
