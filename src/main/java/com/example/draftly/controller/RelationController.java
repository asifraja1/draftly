package com.example.draftly.controller;

import com.example.draftly.dto.RelationRequest;
import com.example.draftly.entity.PendingRelationEmail;
import com.example.draftly.entity.Relation;
import com.example.draftly.repository.PendingRelationEmailRepository;
import com.example.draftly.service.RelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/relations")
@RequiredArgsConstructor
public class RelationController {

    private final RelationService relationService;
    private final PendingRelationEmailRepository pendingRepo;

    private String currentUserEmail(OAuth2AuthenticationToken auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        return auth.getPrincipal().getAttribute("email");
    }

    /**
     * Create or update a relation for the logged-in user.
     * Automatically flushes that user's pending emails for the sender to Kafka.
     */
    @PostMapping
    public ResponseEntity<Relation> createRelation(
            @RequestBody RelationRequest request,
            OAuth2AuthenticationToken auth) {
        String owner = currentUserEmail(auth);
        if (owner == null) return ResponseEntity.status(401).build();
        Relation saved = relationService.saveRelation(request, owner);
        return ResponseEntity.ok(saved);
    }

    /** List only the logged-in user's relations. */
    @GetMapping
    public ResponseEntity<List<Relation>> getMyRelations(OAuth2AuthenticationToken auth) {
        String owner = currentUserEmail(auth);
        if (owner == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(relationService.getRelationsForOwner(owner));
    }

    /** Look up the logged-in user's relation for a specific sender. */
    @GetMapping("/{email}")
    public ResponseEntity<?> getRelation(@PathVariable String email, OAuth2AuthenticationToken auth) {
        String owner = currentUserEmail(auth);
        if (owner == null) return ResponseEntity.status(401).build();
        return relationService.findForOwner(owner, email)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** List the logged-in user's pending (unclassified) emails. */
    @GetMapping("/pending")
    public ResponseEntity<List<PendingRelationEmail>> getPendingEmails(OAuth2AuthenticationToken auth) {
        String owner = currentUserEmail(auth);
        if (owner == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(pendingRepo.findByGmailAccount_GmailAddress(owner));
    }

    /** List the logged-in user's pending emails for a specific sender. */
    @GetMapping("/pending/{senderEmail}")
    public ResponseEntity<List<PendingRelationEmail>> getPendingBySender(
            @PathVariable String senderEmail, OAuth2AuthenticationToken auth) {
        String owner = currentUserEmail(auth);
        if (owner == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(
                pendingRepo.findByGmailAccount_GmailAddressAndSenderEmail(owner, senderEmail));
    }
}
