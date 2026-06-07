package com.example.draftly.service;

import com.example.draftly.dto.DraftEmailKafkaMessage;
import com.example.draftly.dto.SavingEmailKafkaMessage;
import com.example.draftly.entity.Draft;
import com.example.draftly.entity.GmailAccount;
import com.example.draftly.entity.Relation;
import com.example.draftly.repository.DraftRepository;
import com.example.draftly.repository.GmailAccountRepository;
import com.example.draftly.repository.RelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DraftService {

    private final DraftRepository draftRepository;
    private final GmailAccountRepository gmailAccountRepository;
    private final GmailSendService gmailSendService;
    private final KafkaProducerService kafkaProducerService;
    private final RelationRepository relationRepository;

    public List<Draft> getAllDrafts() {
        return draftRepository.findAll();
    }

    public List<Draft> getDraftsByAccount(String gmailAddress) {
        return draftRepository.findByGmailAccount_GmailAddress(gmailAddress);
    }

    public Draft getDraftById(Long id) {
        return draftRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + id));
    }

    public Draft modifyDraft(Long id, String modifiedContent) {
        Draft draft = getDraftById(id);
        draft.setDraftContent(modifiedContent);
        draft.setStatus("MODIFIED");
        return draftRepository.save(draft);
    }

    /**
     * The user answered a NEEDS_INPUT decision. Re-submit the original email to
     * the agent WITH the decision, so it now produces the actual draft.
     */
    public Draft submitDecision(Long id, String decision) {
        Draft draft = getDraftById(id);
        GmailAccount account = draft.getGmailAccount();

        Optional<Relation> relation = relationRepository
                .findByGmailAccountEmailAndEmailAddressIgnoreCase(
                        account.getGmailAddress(), draft.getSenderEmail());

        kafkaProducerService.publishToDraftEmail(
                DraftEmailKafkaMessage.builder()
                        .senderEmail(draft.getSenderEmail())
                        .threadId(draft.getThreadId())
                        .gmailAccountEmail(account.getGmailAddress())
                        .relationName(relation.map(Relation::getRelationName).orElse("Unknown"))
                        .relationContext(relation.map(Relation::getContext).orElse(""))
                        .emailBody(draft.getOriginalEmailBody())
                        .userDecision(decision)
                        .build()
        );

        draft.setStatus("ANSWERED");   // the real draft will arrive as a new row
        return draftRepository.save(draft);
    }

    // Send the draft email and then publish to saving-email topic
    public Draft sendDraft(Long id) throws Exception {
        Draft draft = getDraftById(id);

        GmailAccount account = draft.getGmailAccount();

        String contentToSend = draft.getDraftContent() != null
                ? draft.getDraftContent()
                : draft.getOriginalEmailBody();

        gmailSendService.sendReply(account, draft.getSenderEmail(), draft.getThreadId(), contentToSend);

        draft.setStatus("SENT");
        Draft saved = draftRepository.save(draft);

        // Continuous learning: train on what the user just sent, under the
        // correct relation for this contact.
        Optional<Relation> relation = relationRepository
                .findByGmailAccountEmailAndEmailAddressIgnoreCase(
                        account.getGmailAddress(), draft.getSenderEmail());

        kafkaProducerService.publishToSavingEmail(
                SavingEmailKafkaMessage.builder()
                        .threadId(draft.getThreadId())
                        .senderEmail(draft.getSenderEmail())
                        .body(contentToSend)
                        .gmailAccountEmail(account.getGmailAddress())
                        .relationName(relation.map(Relation::getRelationName).orElse("Unknown"))
                        .relationContext(relation.map(Relation::getContext).orElse(""))
                        .build()
        );

        return saved;
    }
}
