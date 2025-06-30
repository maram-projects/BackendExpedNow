package com.example.ExpedNow.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WebSocketMessage {
    private WebSocketMessageType type;
    private Object payload;
    private String targetUserId;
    private String deliveryId;

    public enum WebSocketMessageType {
        CHAT_MESSAGE,
        MESSAGE_READ,
        USER_TYPING,
        USER_STOP_TYPING,
        USER_ONLINE,
        USER_OFFLINE,
        DELIVERY_UPDATE,
        CHAT_ROOM_CREATED
    }
}
