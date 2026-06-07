package com.example.draftly.dto;

import lombok.Data;

@Data

public class PubSubMessage {

    private MessageData message;

    private String subscription;

    @Data

    public static class MessageData {

        private String data;

        private String messageId;

        private String publishTime;

    }

}
