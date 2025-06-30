package com.example.ExpedNow.models;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "chat_rooms")
@Data
@Builder

public class ChatRoom {
    @Id
    private String id;

    private String deliveryId;
    private String clientId;
    private String deliveryPersonId;

    // Use List instead of Set
    private List<String> participants;

    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Missing fields that are used in ChatService
    private LocalDateTime lastMessageAt;
    private String lastMessageContent;

    // Custom constructor to set default values
    public ChatRoom(String id, String deliveryId, String clientId, String deliveryPersonId,
                    List<String> participants, boolean isActive, LocalDateTime createdAt,
                    LocalDateTime updatedAt, LocalDateTime lastMessageAt, String lastMessageContent) {
        this.id = id;
        this.deliveryId = deliveryId;
        this.clientId = clientId;
        this.deliveryPersonId = deliveryPersonId;
        this.participants = participants;
        this.isActive = isActive != false ? isActive : true;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
        this.lastMessageAt = lastMessageAt;
        this.lastMessageContent = lastMessageContent;
    }

    // Default constructor with default values
    public ChatRoom() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isActive = true;
    }
}