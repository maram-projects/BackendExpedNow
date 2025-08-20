package com.example.ExpedNow.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AIAssistantRequest {

    @NotBlank(message = "Message cannot be empty")
    @Size(max = 1000, message = "Message cannot exceed 1000 characters")
    private String message;

    @NotBlank(message = "User ID is required")
    private String userId;

    private String conversationId;
    private String messageType = "text";
    private Object context; // Additional context if needed

    // Constructors
    public AIAssistantRequest() {}

    public AIAssistantRequest(String message, String userId) {
        this.message = message;
        this.userId = userId;
    }

    // Getters and Setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }
}
