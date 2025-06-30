package com.example.ExpedNow.dto;

import com.example.ExpedNow.models.Message;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Add this annotation

public class ChatMessageRequest {

    @JsonAlias("messageType") // Add this annotation
    @Builder.Default
    private Message.MessageType type = Message.MessageType.TEXT;
    @NotBlank(message = "Receiver ID is required")
    private String receiverId;

    @NotBlank(message = "Delivery ID is required")
    private String deliveryId;

    @NotBlank(message = "Content is required")
    @Size(max = 2000, message = "Message content cannot exceed 2000 characters")
    private String content;


    @Size(max = 500, message = "Attachment URL cannot exceed 500 characters")
    private String attachmentUrl;

    @Size(max = 100, message = "Attachment type cannot exceed 100 characters")
    private String attachmentType;

    // Validation method to check if attachment fields are consistent
    public boolean isAttachmentValid() {
        if (attachmentUrl != null && !attachmentUrl.trim().isEmpty()) {
            return attachmentType != null && !attachmentType.trim().isEmpty();
        }
        return attachmentType == null || attachmentType.trim().isEmpty();
    }

    // Helper method to check if this is an attachment message
    public boolean hasAttachment() {
        return attachmentUrl != null && !attachmentUrl.trim().isEmpty();
    }
}