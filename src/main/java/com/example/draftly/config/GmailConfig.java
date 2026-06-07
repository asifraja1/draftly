package com.example.draftly.config;

import com.example.draftly.entity.GmailAccount;
import com.example.draftly.repository.GmailAccountRepository;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class GmailConfig {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    private final GmailAccountRepository gmailAccountRepository;

    public Gmail getGmailService(GmailAccount gmailAccount) throws Exception {

        // If token expired or close to expiry (within 5 minutes), refresh it
        boolean needsRefresh = gmailAccount.getExpiresAt() == null
                || Instant.now().isAfter(gmailAccount.getExpiresAt().minusSeconds(300));

        if (needsRefresh && gmailAccount.getRefreshToken() != null) {
            try {
                GoogleCredential refreshCred = new GoogleCredential.Builder()
                        .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                        .setJsonFactory(GsonFactory.getDefaultInstance())
                        .setClientSecrets(clientId, clientSecret)
                        .build()
                        .setRefreshToken(gmailAccount.getRefreshToken());

                boolean refreshed = refreshCred.refreshToken();
                if (refreshed) {
                    gmailAccount.setAccessToken(refreshCred.getAccessToken());
                    gmailAccount.setExpiresAt(
                            Instant.now().plusSeconds(refreshCred.getExpiresInSeconds() != null
                                    ? refreshCred.getExpiresInSeconds() : 3600)
                    );
                    gmailAccountRepository.save(gmailAccount);
                    System.out.println("[GmailConfig] Token refreshed for " + gmailAccount.getGmailAddress());
                }

                return new Gmail.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        refreshCred
                ).setApplicationName("Draftly").build();

            } catch (Exception e) {
                System.err.println("[GmailConfig] Token refresh failed: " + e.getMessage());
                // Fall through — try with existing token
            }
        }

        // Use existing access token
        GoogleCredential credential = new GoogleCredential()
                .setAccessToken(gmailAccount.getAccessToken());

        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential
        ).setApplicationName("Draftly").build();
    }
}
