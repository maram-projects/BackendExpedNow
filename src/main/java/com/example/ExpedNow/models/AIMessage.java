package com.example.ExpedNow.models;

import java.time.LocalDateTime;

public class AIMessage {

    private String content;
    private String role; // "user", "assistant", "system"
    private LocalDateTime timestamp;
    private String messageType; // "text", "delivery_info", "payment_info", etc.
    private Object metadata; // Additional data if needed

    // Constructors
    public AIMessage() {
        this.timestamp = LocalDateTime.now();
    }

    public AIMessage(String content, String role) {
        this.content = content;
        this.role = role;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public Object getMetadata() {
        return metadata;
    }

    public void setMetadata(Object metadata) {
        this.metadata = metadata;
    }
}