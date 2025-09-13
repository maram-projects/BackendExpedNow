package com.example.ExpedNow.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatRoomDTO {
    private String id;
    private String deliveryId;
    private String clientId;
    private String clientName;
    private String deliveryPersonId;
    private String deliveryPersonName;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;
    private String lastMessageContent;
    private boolean isActive;
    private int unreadCount;
    private String otherUserId;        // ID of the user you're chatting with
    private String otherUserName;      // Name of the user you're chatting with

    // Add getters and setters
    public String getOtherUserId() {
        return otherUserId;
    }

    public void setOtherUserId(String otherUserId) {
        this.otherUserId = otherUserId;
    }

    public String getOtherUserName() {
        return otherUserName;
    }

    public void setOtherUserName(String otherUserName) {
        this.otherUserName = otherUserName;
    }
}