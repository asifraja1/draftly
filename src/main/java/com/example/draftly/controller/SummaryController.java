package com.example.draftly.controller;

import com.example.draftly.entity.Summary;
import com.example.draftly.repository.SummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/summaries")
@RequiredArgsConstructor
public class SummaryController {

    private final SummaryRepository summaryRepository;

    private String currentUserEmail(OAuth2AuthenticationToken auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        return auth.getPrincipal().getAttribute("email");
    }

    /** List the logged-in user's summaries only. */
    @GetMapping
    public ResponseEntity<List<Summary>> getMySummaries(OAuth2AuthenticationToken auth) {
        String owner = currentUserEmail(auth);
        if (owner == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(summaryRepository.findByGmailAccount_GmailAddress(owner));
    }

    @GetMapping("/account/{gmailAddress}")
    public ResponseEntity<List<Summary>> getSummariesByAccount(@PathVariable String gmailAddress) {
        return ResponseEntity.ok(summaryRepository.findByGmailAccount_GmailAddress(gmailAddress));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Summary> getSummary(@PathVariable Long id) {
        return summaryRepository.findById(id)
                .<ResponseEntity<Summary>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
