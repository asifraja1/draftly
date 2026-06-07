package com.example.draftly.entity;

import jakarta.persistence.*;

import lombok.*;

import java.time.Instant;

@Entity

@Table(name = "email_message")

@Getter

@Setter

@NoArgsConstructor

@AllArgsConstructor

@Builder

public class EmailMessage {

    @Id

    @GeneratedValue(

            strategy = GenerationType.IDENTITY

    )

    private Long id;

    @Column(

            unique = true,

            nullable = false

    )

    private String messageId;

    private String threadId;

    private String sender;

    private String subject;


    @Column(columnDefinition = "TEXT")
    private String body;

    private Instant receivedAt;

    @ManyToOne

    @JoinColumn(

            name = "gmail_account_id"

    )

    private GmailAccount gmailAccount;

}
