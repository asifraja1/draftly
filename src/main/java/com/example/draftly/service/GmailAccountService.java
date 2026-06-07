package com.example.draftly.service;

import com.example.draftly.entity.GmailAccount;

import com.example.draftly.entity.User;

import com.example.draftly.repository.GmailAccountRepository;

import com.example.draftly.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import org.springframework.security.oauth2.core.user.OAuth2User;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service

@RequiredArgsConstructor

public class GmailAccountService {

    private final UserRepository userRepository;

    private final GmailAccountRepository gmailAccountRepository;

    private final OAuth2AuthorizedClientService authorizedClientService;

    public GmailAccount saveAccount(

            OAuth2AuthenticationToken authentication

    ) {

        OAuth2User oauthUser =

                authentication.getPrincipal();

        String email =

                oauthUser.getAttribute("email");

        String name =

                oauthUser.getAttribute("name");

        User user =

                userRepository

                        .findByEmail(email)

                        .orElseGet(() -> {

                            User newUser =

                                    User.builder()

                                            .email(email)

                                            .name(name)

                                            .build();

                            return userRepository.save(

                                    newUser

                            );

                        });

        OAuth2AuthorizedClient client =

                authorizedClientService

                        .loadAuthorizedClient(

                                authentication

                                        .getAuthorizedClientRegistrationId(),

                                authentication.getName()

                        );

        String accessToken =

                client

                        .getAccessToken()

                        .getTokenValue();

        Instant expiresAt =

                client

                        .getAccessToken()

                        .getExpiresAt();

        String refreshToken = null;

        if (client.getRefreshToken() != null) {

            refreshToken =

                    client

                            .getRefreshToken()

                            .getTokenValue();

        }

        GmailAccount gmailAccount =

                gmailAccountRepository

                        .findByGmailAddress(email)

                        .orElse(

                                GmailAccount.builder()

                                        .gmailAddress(email)

                                        .build()

                        );

        gmailAccount.setUser(user);

        gmailAccount.setAccessToken(accessToken);

        gmailAccount.setRefreshToken(refreshToken);

        gmailAccount.setExpiresAt(expiresAt);

        gmailAccount =

                gmailAccountRepository

                        .save(gmailAccount);

        System.out.println("=================================");

        System.out.println("GMAIL ACCOUNT SAVED");

        System.out.println("Email : " + email);

        System.out.println("Name  : " + name);

        System.out.println("=================================");

        return gmailAccount;

    }

}