package com.example.draftly.controller;

import com.example.draftly.service.GmailAccountService;
import com.example.draftly.service.GmailSyncService;
import com.example.draftly.service.GmailWatchService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final GmailAccountService gmailAccountService;
    private final GmailSyncService gmailSyncService;
    private final GmailWatchService gmailWatchService;

    @GetMapping("/auth/success")
    public void authSuccess(OAuth2AuthenticationToken authentication, HttpServletResponse response) throws IOException {
        try {
            gmailAccountService.saveAccount(authentication);
            gmailSyncService.syncEmails(authentication);
            gmailWatchService.startWatch(authentication);
        } catch (Exception e) {
            e.printStackTrace();
        }
        response.sendRedirect("http://localhost:3000/train");
    }
}
