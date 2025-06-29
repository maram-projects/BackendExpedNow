package com.example.ExpedNow.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "chat_rooms")
public class ChatRoom {
    @Id
    private String id;

    private String deliveryId;
    private String clientId;
    private String deliveryPersonId;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime lastMessageAt;
    private String lastMessageContent;

    @Builder.Default
    private Set<String> participants = new HashSet<>();

    @Builder.Default
    private boolean isActive = true;

    // Generate chat room ID based on delivery
    public static String generateChatRoomId(String deliveryId) {
        return "chat_" + deliveryId;
    }
}