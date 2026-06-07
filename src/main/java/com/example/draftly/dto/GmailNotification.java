package com.example.draftly.dto;

import lombok.Data;

import java.math.BigInteger;

@Data

public class GmailNotification {

    private String emailAddress;

    private BigInteger historyId;

}
