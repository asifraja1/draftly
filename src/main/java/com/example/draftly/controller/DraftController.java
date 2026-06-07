package com.example.draftly.controller;

import com.example.draftly.dto.DraftModifyRequest;
import com.example.draftly.entity.Draft;
import com.example.draftly.service.DraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drafts")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService draftService;

    private String currentUserEmail(OAuth2AuthenticationToken auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        return auth.getPrincipal().getAttribute("email");
    }

    /** List the logged-in user's drafts only. */
    @GetMapping
    public ResponseEntity<List<Draft>> getMyDrafts(OAuth2AuthenticationToken auth) {
        String owner = currentUserEmail(auth);
        if (owner == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(draftService.getDraftsByAccount(owner));
    }

    /** List drafts for a specific Gmail account. */
    @GetMapping("/account/{gmailAddress}")
    public ResponseEntity<List<Draft>> getDraftsByAccount(@PathVariable String gmailAddress) {
        return ResponseEntity.ok(draftService.getDraftsByAccount(gmailAddress));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Draft> getDraft(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(draftService.getDraftById(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Modify the draft content.
     * Status changes from PENDING → MODIFIED.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Draft> modifyDraft(
            @PathVariable Long id,
            @RequestBody DraftModifyRequest request
    ) {
        try {
            Draft updated = draftService.modifyDraft(id, request.getModifiedContent());
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Send the draft as a reply email to the original sender.
     * Publishes to saving-email Kafka topic after sending.
     * Status changes to SENT.
     */
    @PostMapping("/{id}/send")
    public ResponseEntity<Draft> sendDraft(@PathVariable Long id) {
        try {
            Draft sent = draftService.sendDraft(id);
            return ResponseEntity.ok(sent);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Answer a NEEDS_INPUT decision. Re-submits the email to the agent with the
     * user's choice so it generates the actual draft.
     * Body: { "decision": "Accept" }
     */
    @PostMapping("/{id}/decision")
    public ResponseEntity<Draft> submitDecision(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body
    ) {
        try {
            Draft updated = draftService.submitDecision(id, body.getOrDefault("decision", ""));
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
