package com.example.draftly.service;

import com.example.draftly.entity.EmailMessage;

import com.example.draftly.entity.GmailAccount;

import com.example.draftly.repository.EmailMessageRepository;

import com.google.api.services.gmail.model.Message;

import com.google.api.services.gmail.model.MessagePartHeader;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailSaveService {
    private final EmailMessageRepository emailRepository;
    public void saveEmail(
            Message gmailMessage,
            GmailAccount gmailAccount
    ) {
        if (emailRepository.existsByMessageId(
                gmailMessage.getId()
        )) {
            return;
        }
        String subject = "";
        String from = "";
        if (gmailMessage.getPayload() != null &&
                gmailMessage.getPayload().getHeaders() != null) {
            for (MessagePartHeader header :
                    gmailMessage.getPayload().getHeaders()) {
                if ("Subject".equalsIgnoreCase(
                        header.getName()
                )) {
                    subject = header.getValue();
                }
                if ("From".equalsIgnoreCase(
                        header.getName()
                )) {
                    from = header.getValue();
                }
            }
        }
        EmailMessage email =
                EmailMessage.builder()
                        .messageId(
                                gmailMessage.getId()
                        )
                        .threadId(
                                gmailMessage.getThreadId()
                        )
                        .sender(from)
                        .subject(subject)
                        .body(
                                gmailMessage.getSnippet()
                        )
                        .gmailAccount(
                                gmailAccount
                        )
                        .build();
        emailRepository.save(email);
        System.out.println();
        System.out.println("==================================");
        System.out.println("NEW EMAIL SAVED");
        System.out.println("FROM    : " + from);
        System.out.println("SUBJECT : " + subject);
        System.out.println("BODY    : " +
                gmailMessage.getSnippet());
        System.out.println("==================================");
        System.out.println();
    }
}
