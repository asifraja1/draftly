package com.example.draftly.repository;

import com.example.draftly.entity.Relation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RelationRepository extends JpaRepository<Relation, Long> {

    // ── Owner-scoped (multi-user) ──────────────────────────────────────────────
    Optional<Relation> findByGmailAccountEmailAndEmailAddressIgnoreCase(String gmailAccountEmail, String emailAddress);
    List<Relation> findByGmailAccountEmail(String gmailAccountEmail);

    // ── Legacy (kept for backward compatibility) ───────────────────────────────
    Optional<Relation> findByEmailAddressIgnoreCase(String emailAddress);
}
