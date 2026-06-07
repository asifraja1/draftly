package com.example.draftly.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Stores emails/threads that arrived but had no relation — waits for user to supply one.
@Entity
@Table(name = "pending_relation_email")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingRelationEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String senderEmail;

    private String threadId;

    private String messageId;

    // WEBHOOK or BATCH — which flow created this pending record
    private String source;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @ManyToOne
    @JoinColumn(name = "gmail_account_id")
    private GmailAccount gmailAccount;
}
