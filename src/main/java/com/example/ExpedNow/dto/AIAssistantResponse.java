package com.example.ExpedNow.dto;

import com.example.ExpedNow.models.AIMessage;

import java.time.LocalDateTime;
import java.util.List;

public class AIAssistantResponse {

    private boolean success;
    private String message;
    private String conversationId;
    private LocalDateTime timestamp;
    private List<AIMessage> messages; // For conversation history
    private String error;
    private Object metadata; // Additional response data if needed

    // Constructors
    public AIAssistantResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public AIAssistantResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public List<AIMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AIMessage> messages) {
        this.messages = messages;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Object getMetadata() {
        return metadata;
    }

    public void setMetadata(Object metadata) {
        this.metadata = metadata;
    }
}