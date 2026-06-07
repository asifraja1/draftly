package com.example.draftly.service;

import com.example.draftly.config.GmailConfig;

import com.example.draftly.entity.GmailAccount;

import com.example.draftly.repository.GmailAccountRepository;

import com.google.api.services.gmail.Gmail;

import com.google.api.services.gmail.model.ListMessagesResponse;

import com.google.api.services.gmail.model.Message;

import lombok.RequiredArgsConstructor;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import org.springframework.security.oauth2.core.user.OAuth2User;

import org.springframework.stereotype.Service;

@Service

@RequiredArgsConstructor

public class GmailSyncService {

    private final GmailConfig gmailConfig;

    private final GmailAccountRepository gmailAccountRepository;

    private final EmailSaveService emailSaveService;

    public void syncEmails(OAuth2AuthenticationToken authentication) throws Exception {
        OAuth2User oauthUser = authentication.getPrincipal();
        String email = oauthUser.getAttribute("email");
        GmailAccount gmailAccount = gmailAccountRepository
                        .findByGmailAddress(email)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Gmail account not found"
                                )
                        );
        Gmail gmail = gmailConfig.getGmailService(gmailAccount);
        ListMessagesResponse response = gmail.users()
                        .messages()
                        .list("me")
                        .setMaxResults(20L)
                        .execute();
        if (response.getMessages() == null) {
            System.out.println(
                    "No emails found."
            );
            return;
        }
        for (Message messageRef : response.getMessages()) {
            Message fullMessage = gmail.users()
                            .messages()
                            .get(
                                    "me",
                                    messageRef.getId()
                            )
                            .execute();
            emailSaveService.saveEmail(
                    fullMessage,
                    gmailAccount
            );
        }
        System.out.println("=================================");
        System.out.println("INITIAL SYNC COMPLETED");
        System.out.println("Total Emails : "
                + response.getMessages().size());
        System.out.println("=================================");
    }
}