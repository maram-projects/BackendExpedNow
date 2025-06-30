package com.example.ExpedNow.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore any unknown fields
public class TypingIndicator {

    @NotBlank(message = "Sender ID is required")
    private String senderId;

    @NotBlank(message = "Receiver ID is required")
    private String receiverId;

    @NotBlank(message = "Delivery ID is required")
    private String deliveryId;

    // Handle both "typing" and "isTyping" field names
    @JsonProperty("typing") // This will be used when serializing to JSON
    @JsonAlias({"isTyping", "typing"}) // This allows both field names when deserializing from JSON
    private boolean typing;

    // Alternative constructor for convenience
    public TypingIndicator(String senderId, String receiverId, String deliveryId, boolean typing) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.deliveryId = deliveryId;
        this.typing = typing;
    }

    // Helper methods
    public boolean isTyping() {
        return this.typing;
    }

    public void setTyping(boolean typing) {
        this.typing = typing;
    }

    // Alternative setter for "isTyping" field name compatibility
    public void setIsTyping(boolean isTyping) {
        this.typing = isTyping;
    }

    public boolean getIsTyping() {
        return this.typing;
    }
}