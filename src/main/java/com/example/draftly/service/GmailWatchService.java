package com.example.draftly.service;

import com.example.draftly.config.GmailConfig;

import com.example.draftly.entity.GmailAccount;

import com.example.draftly.repository.GmailAccountRepository;

import com.google.api.services.gmail.Gmail;

import com.google.api.services.gmail.model.WatchRequest;

import com.google.api.services.gmail.model.WatchResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.oauth2.core.user.OAuth2User;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import org.springframework.stereotype.Service;

@Service

@RequiredArgsConstructor

public class GmailWatchService {

    private final GmailAccountRepository gmailAccountRepository;

    private final GmailConfig gmailConfig;

    public void startWatch(OAuth2AuthenticationToken authentication) throws Exception {
        OAuth2User oauthUser = authentication.getPrincipal();
        String email = oauthUser.getAttribute("email");
        GmailAccount gmailAccount = gmailAccountRepository
                .findByGmailAddress(email)
                .orElseThrow(() ->
                                new RuntimeException("Gmail account not found"));
        Gmail gmail = gmailConfig.getGmailService(gmailAccount);
        WatchRequest request = new WatchRequest().setTopicName("projects/draftly-498009/topics/gmail-topic");
        WatchResponse response = gmail.users()
                .watch("me", request).execute();
        gmailAccount.setHistoryId(response.getHistoryId());
        gmailAccountRepository.save(gmailAccount);

        System.out.println("=================================");

        System.out.println("GMAIL WATCH STARTED");

        System.out.println("Email      : " + email);

        System.out.println("History ID : "

                + response.getHistoryId());

        System.out.println("Expiration : "

                + response.getExpiration());

        System.out.println("=================================");

    }

}
