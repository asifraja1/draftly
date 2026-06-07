package com.example.draftly.controller;

import com.example.draftly.dto.PubSubMessage;

import com.example.draftly.service.GmailHistoryService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

@RestController

@RequiredArgsConstructor

public class GmailWebhookController {

    private final GmailHistoryService gmailHistoryService;

    @PostMapping("/gmail/webhook")

    public ResponseEntity<String> webhook(

            @RequestBody PubSubMessage request

    ) {

        try {

            System.out.println(

                    "WEBHOOK HIT"

            );

            gmailHistoryService.processNotification(

                    request.getMessage().getData()

            );

            return ResponseEntity.ok(

                    "SUCCESS"

            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity.internalServerError()

                    .body(

                            "ERROR"

                    );

        }

    }

}
