package com.example.ExpedNow.dto;

import com.example.ExpedNow.models.Message;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MessageDTO {
    private String id;
    private String senderId;
    private String senderName;
    private String receiverId;
    private String deliveryId;
    private String content;
    private Message.MessageType type;
    private Message.MessageStatus status;
    private LocalDateTime timestamp;
    private LocalDateTime readAt;
    private String attachmentUrl;
    private String attachmentType;
}