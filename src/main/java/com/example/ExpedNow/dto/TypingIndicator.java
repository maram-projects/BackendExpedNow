package com.example.ExpedNow.dto;

import lombok.Data;

@Data
public class TypingIndicator {
    private String senderId;
    private String receiverId;
    private String deliveryId;
    private boolean isTyping;
}