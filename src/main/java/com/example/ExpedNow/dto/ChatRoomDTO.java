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
}