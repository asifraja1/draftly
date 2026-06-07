package com.example.draftly.dto;

import lombok.Data;

@Data
public class RelationRequest {
    private String emailAddress;
    private String relationName;
    private String context;
}
