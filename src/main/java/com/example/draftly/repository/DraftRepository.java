package com.example.draftly.repository;

import com.example.draftly.entity.Draft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DraftRepository extends JpaRepository<Draft, Long> {
    List<Draft> findByGmailAccount_GmailAddress(String gmailAddress);
    List<Draft> findByStatus(String status);
}
