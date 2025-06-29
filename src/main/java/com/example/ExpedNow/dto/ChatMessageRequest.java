package com.example.ExpedNow.dto;

import com.example.ExpedNow.models.Message;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class ChatMessageRequest {
    @NotBlank(message = "Receiver ID is required")
    private String receiverId;

    @NotBlank(message = "Delivery ID is required")
    private String deliveryId;

    @NotBlank(message = "Content is required")
    private String content;

    private Message.MessageType type = Message.MessageType.TEXT;
    private String attachmentUrl;
    private String attachmentType;
}
