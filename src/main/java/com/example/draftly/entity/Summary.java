package com.example.draftly.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Summary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String threadId;

    private String senderEmail;

    @Column(columnDefinition = "TEXT")
    private String summaryText;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @ManyToOne
    @JoinColumn(name = "gmail_account_id")
    private GmailAccount gmailAccount;
}
