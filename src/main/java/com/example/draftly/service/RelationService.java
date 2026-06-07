package com.example.draftly.service;

import com.example.draftly.dto.DraftEmailKafkaMessage;
import com.example.draftly.dto.RelationRequest;
import com.example.draftly.dto.SavingEmailKafkaMessage;
import com.example.draftly.entity.GmailAccount;
import com.example.draftly.entity.PendingRelationEmail;
import com.example.draftly.entity.Relation;
import com.example.draftly.repository.EmailMessageRepository;
import com.example.draftly.repository.GmailAccountRepository;
import com.example.draftly.repository.PendingRelationEmailRepository;
import com.example.draftly.repository.RelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RelationService {

    private final RelationRepository relationRepository;
    private final PendingRelationEmailRepository pendingRepo;
    private final GmailAccountRepository gmailAccountRepository;
    private final KafkaProducerService kafkaProducerService;
    private final EmailMessageRepository emailMessageRepository;
    private final EmailBatchProcessService emailBatchProcessService;

    /**
     * Create or update a relation, scoped to the owner (the logged-in Gmail account).
     * Two different users can each have their own relation for the same sender.
     */
    public Relation saveRelation(RelationRequest request, String ownerEmail) {
        String owner = norm(ownerEmail);
        String email = norm(request.getEmailAddress());

        Relation relation = relationRepository
                .findByGmailAccountEmailAndEmailAddressIgnoreCase(owner, email)
                .orElse(Relation.builder()
                        .gmailAccountEmail(owner)
                        .emailAddress(email)
                        .build());
        relation.setRelationName(request.getRelationName());
        relation.setContext(request.getContext());
        Relation saved = relationRepository.save(relation);

        System.out.println("[RELATION] Saved relation for owner=" + owner + " sender=" + email);

        // flush this owner's pending emails for this sender
        flushPendingEmails(saved, owner);

        // Train on the user's full sent-email history with this contact (async)
        emailBatchProcessService.trainOnSentEmailsTo(owner, email, saved);

        return saved;
    }

    // After a relation is created, publish this owner's pending emails for that sender.
    private void flushPendingEmails(Relation relation, String owner) {
        List<PendingRelationEmail> pending = pendingRepo
                .findByGmailAccount_GmailAddressAndSenderEmail(owner, relation.getEmailAddress());
        if (pending.isEmpty()) return;

        for (PendingRelationEmail p : pending) {
            GmailAccount account = p.getGmailAccount();
            String body = emailMessageRepository.findByMessageId(p.getMessageId())
                    .map(e -> e.getBody()).orElse("");

            if ("BATCH".equals(p.getSource())) {
                String subject = emailMessageRepository.findByMessageId(p.getMessageId())
                        .map(e -> e.getSubject()).orElse("");

                kafkaProducerService.publishToSavingEmail(
                        SavingEmailKafkaMessage.builder()
                                .messageId(p.getMessageId())
                                .threadId(p.getThreadId())
                                .senderEmail(p.getSenderEmail())
                                .subject(subject)
                                .body(body)
                                .gmailAccountEmail(account.getGmailAddress())
                                .relationName(relation.getRelationName())
                                .relationContext(relation.getContext())
                                .build()
                );
            } else {
                kafkaProducerService.publishToDraftEmail(
                        DraftEmailKafkaMessage.builder()
                                .senderEmail(p.getSenderEmail())
                                .threadId(p.getThreadId())
                                .gmailAccountEmail(account.getGmailAddress())
                                .relationName(relation.getRelationName())
                                .relationContext(relation.getContext())
                                .emailBody(body)
                                .build()
                );
            }
        }

        pendingRepo.deleteAll(pending);
        System.out.println("[RELATION] Flushed " + pending.size() + " pending emails for " + relation.getEmailAddress());
    }

    /** All relations belonging to one owner (logged-in user). */
    public List<Relation> getRelationsForOwner(String ownerEmail) {
        return relationRepository.findByGmailAccountEmail(norm(ownerEmail));
    }

    /** Look up a relation for a sender, scoped to the owner. */
    public Optional<Relation> findForOwner(String ownerEmail, String senderEmail) {
        return relationRepository.findByGmailAccountEmailAndEmailAddressIgnoreCase(
                norm(ownerEmail), norm(senderEmail));
    }

    private String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}
