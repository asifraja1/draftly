package com.example.draftly.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topic.saving-email}")
    private String savingEmailTopic;

    @Value("${kafka.topic.draft-email}")
    private String draftEmailTopic;

    @Value("${kafka.topic.output}")
    private String outputTopic;

    @Bean
    public NewTopic savingEmailTopic() {
        return TopicBuilder.name(savingEmailTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic draftEmailTopic() {
        return TopicBuilder.name(draftEmailTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic outputTopic() {
        return TopicBuilder.name(outputTopic).partitions(3).replicas(1).build();
    }
}
