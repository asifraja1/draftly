package com.example.draftly.repository;

import com.example.draftly.entity.PendingRelationEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PendingRelationEmailRepository extends JpaRepository<PendingRelationEmail, Long> {
    List<PendingRelationEmail> findBySenderEmail(String senderEmail);

    // ── Owner-scoped (multi-user) ──────────────────────────────────────────────
    List<PendingRelationEmail> findByGmailAccount_GmailAddress(String gmailAddress);
    List<PendingRelationEmail> findByGmailAccount_GmailAddressAndSenderEmail(String gmailAddress, String senderEmail);
}
