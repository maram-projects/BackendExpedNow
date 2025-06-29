package com.example.ExpedNow.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "messages")
public class Message {
    @Id
    private String id;

    private String senderId;
    private String receiverId;
    private String deliveryId; // Associated delivery
    private String content;

    @Builder.Default
    private MessageType type = MessageType.TEXT;

    @Builder.Default
    private MessageStatus status = MessageStatus.SENT;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private LocalDateTime readAt;
    private String attachmentUrl;
    private String attachmentType;

    public enum MessageType {
        TEXT, IMAGE, DOCUMENT, LOCATION, SYSTEM_NOTIFICATION
    }

    public enum MessageStatus {
        SENT, DELIVERED, READ
    }
}