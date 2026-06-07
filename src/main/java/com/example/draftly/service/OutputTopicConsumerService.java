package com.example.draftly.service;

import com.example.draftly.dto.OutputKafkaMessage;
import com.example.draftly.entity.Draft;
import com.example.draftly.entity.GmailAccount;
import com.example.draftly.entity.Summary;
import com.example.draftly.repository.DraftRepository;
import com.example.draftly.repository.GmailAccountRepository;
import com.example.draftly.repository.SummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class sOutputTopicConsumerService {

    private final SummaryRepository summaryRepository;
    private final DraftRepository draftRepository;
    private final GmailAccountRepository gmailAccountRepository;
    private final SseEmitterService sseEmitterService;

    @KafkaListener(
            topics = "${kafka.topic.output}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(OutputKafkaMessage message) {
        System.out.println("[KAFKA OUTPUT] Received message type=" + message.getType()
                + " thread=" + message.getThreadId());

        GmailAccount account = gmailAccountRepository
                .findByGmailAddress(message.getGmailAccountEmail())
                .orElse(null);

        if ("SUMMARY".equalsIgnoreCase(message.getType())) {
            handleSummary(message, account);
        } else if ("DRAFT".equalsIgnoreCase(message.getType())) {
            handleDraft(message, account);
        } else if ("NEEDS_INPUT".equalsIgnoreCase(message.getType())) {
            handleNeedsInput(message, account);
        } else {
            System.out.println("[KAFKA OUTPUT] Unknown type: " + message.getType());
        }
    }

    private void handleNeedsInput(OutputKafkaMessage message, GmailAccount account) {
        Draft draft = Draft.builder()
                .threadId(message.getThreadId())
                .senderEmail(message.getSenderEmail())
                .originalEmailBody(message.getOriginalEmailBody())
                .draftContent(null)
                .status("NEEDS_INPUT")
                .question(message.getQuestion())
                .decisionOptions(message.getOptions())
                .gmailAccount(account)
                .build();
        Draft saved = draftRepository.save(draft);
        System.out.println("[OUTPUT] Needs-input saved for thread=" + message.getThreadId());
        sseEmitterService.broadcast("needs_input", Map.of(
                "id", saved.getId(),
                "threadId", saved.getThreadId(),
                "senderEmail", saved.getSenderEmail() != null ? saved.getSenderEmail() : "",
                "question", saved.getQuestion() != null ? saved.getQuestion() : "",
                "options", saved.getDecisionOptions() != null ? saved.getDecisionOptions() : "[]"
        ));
    }

    private void handleSummary(OutputKafkaMessage message, GmailAccount account) {
        Summary summary = Summary.builder()
                .threadId(message.getThreadId())
                .senderEmail(message.getSenderEmail())
                .summaryText(message.getContent())
                .gmailAccount(account)
                .build();
        Summary saved = summaryRepository.save(summary);
        System.out.println("[OUTPUT] Summary saved for thread=" + message.getThreadId());
        sseEmitterService.broadcast("summary", Map.of(
                "id", saved.getId(),
                "threadId", saved.getThreadId(),
                "senderEmail", saved.getSenderEmail() != null ? saved.getSenderEmail() : "",
                "summaryText", saved.getSummaryText() != null ? saved.getSummaryText() : ""
        ));
    }

    private void handleDraft(OutputKafkaMessage message, GmailAccount account) {
        Draft draft = Draft.builder()
                .threadId(message.getThreadId())
                .senderEmail(message.getSenderEmail())
                .originalEmailBody(message.getOriginalEmailBody())
                .draftContent(message.getContent())
                .status("PENDING")
                .gmailAccount(account)
                .build();
        Draft saved = draftRepository.save(draft);
        System.out.println("[OUTPUT] Draft saved for thread=" + message.getThreadId());
        sseEmitterService.broadcast("draft", Map.of(
                "id", saved.getId(),
                "threadId", saved.getThreadId(),
                "senderEmail", saved.getSenderEmail() != null ? saved.getSenderEmail() : "",
                "draftContent", saved.getDraftContent() != null ? saved.getDraftContent() : "",
                "status", saved.getStatus()
        ));
    }
}
