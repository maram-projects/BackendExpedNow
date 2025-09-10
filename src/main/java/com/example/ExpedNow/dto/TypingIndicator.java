package com.example.ExpedNow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypingIndicator {
    private String senderId;
    private String receiverId;
    private String deliveryId;
    private boolean isTyping;
    private LocalDateTime timestamp;

    // Constructor for convenience
    public TypingIndicator(String senderId, String receiverId, String deliveryId, boolean isTyping) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.deliveryId = deliveryId;
        this.isTyping = isTyping;
        this.timestamp = LocalDateTime.now();
    }

    // Convenience methods
    public boolean isTyping() {
        return isTyping;
    }

    public void setTyping(boolean typing) {
        this.isTyping = typing;
    }
}