package com.example.draftly.repository;

import com.example.draftly.entity.Summary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SummaryRepository extends JpaRepository<Summary, Long> {
    List<Summary> findByGmailAccount_GmailAddress(String gmailAddress);
}
