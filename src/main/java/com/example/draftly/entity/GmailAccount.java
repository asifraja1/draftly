package com.example.draftly.entity;

import jakarta.persistence.*;

import lombok.*;

import java.math.BigInteger;
import java.time.Instant;

@Entity

@Table(name = "gmail_account")

@Getter

@Setter

@NoArgsConstructor

@AllArgsConstructor

@Builder

public class GmailAccount {

    @Id

    @GeneratedValue(

            strategy = GenerationType.IDENTITY

    )

    private Long id;

    @Column(

            unique = true,

            nullable = false

    )

    private String gmailAddress;

    @Column(

            columnDefinition = "TEXT"

    )

    private String accessToken;

    @Column(

            columnDefinition = "TEXT"

    )

    private String refreshToken;

    private BigInteger historyId;

    private Instant expiresAt;

    @OneToOne

    @JoinColumn(

            name = "user_id"

    )

    private User user;

}