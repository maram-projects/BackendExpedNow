package com.example.ExpedNow.controllers;

import com.example.ExpedNow.config.WebSocketEventListener;
import com.example.ExpedNow.dto.*;
import com.example.ExpedNow.services.core.impl.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
// WebSocketChatController.java
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketEventListener webSocketEventListener;

    @MessageMapping("/chat/send")
    public void sendMessage(@Payload ChatMessageRequest request, Authentication authentication) {
        try {
            MessageDTO message = chatService.sendMessage(request, authentication);
            log.info("WebSocket message sent: {}", message.getId());

            // Send to sender (for confirmation)
            messagingTemplate.convertAndSendToUser(
                    message.getSenderId(),
                    "/queue/messages",
                    new WebSocketMessage(
                            WebSocketMessage.WebSocketMessageType.CHAT_MESSAGE,
                            message,
                            message.getReceiverId(),
                            message.getDeliveryId()
                    )
            );

            // Send to recipient
            messagingTemplate.convertAndSendToUser(
                    message.getReceiverId(),
                    "/queue/messages",
                    new WebSocketMessage(
                            WebSocketMessage.WebSocketMessageType.CHAT_MESSAGE,
                            message,
                            message.getReceiverId(),
                            message.getDeliveryId()
                    )
            );
        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
        }
    }

    @MessageMapping("/chat/typing")
    public void handleTyping(@Payload TypingIndicator indicator, Principal principal) {
        try {
            WebSocketMessage wsMessage = new WebSocketMessage(
                    WebSocketMessage.WebSocketMessageType.USER_TYPING,
                    indicator,
                    indicator.getReceiverId(),
                    indicator.getDeliveryId()
            );

            // Check if recipient is online before sending
            if (webSocketEventListener.isUserOnline(indicator.getReceiverId())) {
                messagingTemplate.convertAndSendToUser(
                        indicator.getReceiverId(),
                        "/queue/typing",
                        wsMessage
                );
            }
        } catch (Exception e) {
            log.error("Error handling typing indicator", e);
        }
    }

    @MessageMapping("/chat/stop-typing")
    public void handleStopTyping(@Payload TypingIndicator indicator, Principal principal) {
        try {
            WebSocketMessage wsMessage = new WebSocketMessage(
                    WebSocketMessage.WebSocketMessageType.USER_STOP_TYPING,
                    indicator,
                    indicator.getReceiverId(),
                    indicator.getDeliveryId()
            );

            if (webSocketEventListener.isUserOnline(indicator.getReceiverId())) {
                messagingTemplate.convertAndSendToUser(
                        indicator.getReceiverId(),
                        "/queue/typing",
                        wsMessage
                );
            }
        } catch (Exception e) {
            log.error("Error handling stop typing indicator", e);
        }
    }
}