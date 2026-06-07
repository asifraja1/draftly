package com.example.draftly.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "relation",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_relation_owner_email",
        columnNames = {"gmail_account_email", "email_address"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Relation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The owner: the Gmail account this relation belongs to. Scopes relations per user. */
    @Column(name = "gmail_account_email")
    private String gmailAccountEmail;

    @Column(name = "email_address", nullable = false)
    private String emailAddress;

    private String relationName;

    @Column(columnDefinition = "TEXT")
    private String context;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
