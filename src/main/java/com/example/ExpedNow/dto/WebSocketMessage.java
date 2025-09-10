package com.example.ExpedNow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {

    private WebSocketMessageType type;
    private Object payload;
    private String targetUserId;
    private String deliveryId;
    private LocalDateTime timestamp;

    // Constructor without timestamp (will be set automatically)
    public WebSocketMessage(WebSocketMessageType type, Object payload, String targetUserId, String deliveryId) {
        this.type = type;
        this.payload = payload;
        this.targetUserId = targetUserId;
        this.deliveryId = deliveryId;
        this.timestamp = LocalDateTime.now();
    }

    public enum WebSocketMessageType {
        CHAT_MESSAGE,
        MESSAGE_READ,
        MESSAGE_DELIVERED,
        USER_ONLINE,
        USER_OFFLINE,
        TYPING_START,
        TYPING_STOP,
        CONNECTION_ESTABLISHED,
        CONNECTION_CLOSED,
        HEARTBEAT,
        SYSTEM_NOTIFICATION,
        ERROR
    }
}