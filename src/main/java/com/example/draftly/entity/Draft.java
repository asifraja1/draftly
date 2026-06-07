package com.example.draftly.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "draft")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Draft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String threadId;

    private String senderEmail;

    @Column(columnDefinition = "TEXT")
    private String originalEmailBody;

    @Column(columnDefinition = "TEXT")
    private String draftContent;

    @Builder.Default
    private String status = "PENDING"; // PENDING, MODIFIED, SENT, NEEDS_INPUT

    // For NEEDS_INPUT drafts — a decision the user must make before drafting
    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String decisionOptions; // JSON array string

    @Builder.Default
    private Instant createdAt = Instant.now();

    @ManyToOne
    @JoinColumn(name = "gmail_account_id")
    private GmailAccount gmailAccount;
}
