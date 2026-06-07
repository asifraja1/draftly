package com.example.draftly.repository;

import com.example.draftly.entity.EmailMessage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import java.util.List;

public interface EmailMessageRepository

        extends JpaRepository<EmailMessage, Long> {

    Optional<EmailMessage> findByMessageId(

            String messageId

    );

    List<EmailMessage> findByGmailAccountId(

            Long gmailAccountId

    );

    boolean existsByMessageId(

            String messageId

    );

    // Thread context for the gRPC server — newest first, scoped to one account
    List<EmailMessage> findByThreadIdAndGmailAccount_GmailAddressOrderByReceivedAtDesc(
            String threadId, String gmailAddress);

    // Fallback when account email is unknown — by thread only
    List<EmailMessage> findByThreadIdOrderByReceivedAtDesc(String threadId);

}