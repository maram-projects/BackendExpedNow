package com.example.ExpedNow.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "ai_conversations")
public class AIConversation {

    @Id
    private String id;

    @Indexed
    private String userId;

    private LocalDateTime startTime;
    private LocalDateTime lastActivity;

    private List<AIMessage> messages = new ArrayList<>();

    private String title; // Optional conversation title
    private boolean active = true;

    // Constructors
    public AIConversation() {}

    public AIConversation(String userId) {
        this.userId = userId;
        this.startTime = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
    }

    // Helper methods
    public void addMessage(AIMessage message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
        this.lastActivity = LocalDateTime.now();
    }

    public void updateLastActivity() {
        this.lastActivity = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(LocalDateTime lastActivity) {
        this.lastActivity = lastActivity;
    }

    public List<AIMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AIMessage> messages) {
        this.messages = messages;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
