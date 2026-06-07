package com.example.draftly.service;

import com.example.draftly.dto.DraftEmailKafkaMessage;
import com.example.draftly.dto.SavingEmailKafkaMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.saving-email}")
    private String savingEmailTopic;

    @Value("${kafka.topic.draft-email}")
    private String draftEmailTopic;

    @Async
    public void publishToSavingEmail(SavingEmailKafkaMessage message) {
        kafkaTemplate.send(savingEmailTopic, message.getThreadId(), message);
        System.out.println("[KAFKA] Published to saving-email | thread=" + message.getThreadId()
                + " | sender=" + message.getSenderEmail());
    }

    @Async
    public void publishToDraftEmail(DraftEmailKafkaMessage message) {
        kafkaTemplate.send(draftEmailTopic, message.getThreadId(), message);
        System.out.println("[KAFKA] Published to draft-email  | thread=" + message.getThreadId()
                + " | sender=" + message.getSenderEmail());
    }
}
