package com.example.draftly.repository;

import com.example.draftly.entity.GmailAccount;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GmailAccountRepository

        extends JpaRepository<GmailAccount, Long> {

    Optional<GmailAccount> findByGmailAddress(

            String gmailAddress

    );

    Optional<GmailAccount> findByUserId(

            Long userId

    );

}
