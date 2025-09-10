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
import java.util.Map;

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
            log.info("WebSocket message received from: {} to: {} for delivery: {}",
                    authentication.getName(), request.getReceiverId(), request.getDeliveryId());

            MessageDTO message = chatService.sendMessage(request, authentication);
            log.info("Message processed with ID: {}", message.getId());

            // Create WebSocket message
            WebSocketMessage wsMessage = new WebSocketMessage(
                    WebSocketMessage.WebSocketMessageType.CHAT_MESSAGE,
                    message,
                    request.getReceiverId(),
                    request.getDeliveryId()
            );

            // **الأهم** - Send to RECEIVER first (this fixes the delivery person not getting messages)
            messagingTemplate.convertAndSendToUser(
                    request.getReceiverId(),  // Receiver ID
                    "/queue/messages",        // Queue path
                    wsMessage
            );
            log.info("Message sent to receiver: {}", request.getReceiverId());

            // Send to SENDER for confirmation
            messagingTemplate.convertAndSendToUser(
                    authentication.getName(), // Sender ID
                    "/queue/messages",
                    wsMessage
            );
            log.info("Message confirmation sent to sender: {}", authentication.getName());

        } catch (Exception e) {
            log.error("Error handling WebSocket message from {}: {}",
                    authentication.getName(), e.getMessage(), e);

            // Send error message back to sender
            WebSocketMessage errorMessage = new WebSocketMessage(
                    WebSocketMessage.WebSocketMessageType.ERROR,
                    Map.of("error", "Failed to send message", "details", e.getMessage()),
                    authentication.getName(),
                    request.getDeliveryId()
            );

            messagingTemplate.convertAndSendToUser(
                    authentication.getName(),
                    "/queue/messages",
                    errorMessage
            );
        }
    }

    @MessageMapping("/chat/typing")
    public void handleTyping(@Payload TypingIndicator indicator, Authentication authentication) {
        try {
            log.debug("Typing indicator from: {} to: {} for delivery: {}",
                    authentication.getName(), indicator.getReceiverId(), indicator.getDeliveryId());

            WebSocketMessage wsMessage = new WebSocketMessage(
                    WebSocketMessage.WebSocketMessageType.TYPING_START,
                    indicator,
                    indicator.getReceiverId(),
                    indicator.getDeliveryId()
            );

            // Send typing indicator to receiver
            if (webSocketEventListener.isUserOnline(indicator.getReceiverId())) {
                messagingTemplate.convertAndSendToUser(
                        indicator.getReceiverId(),
                        "/queue/typing",
                        wsMessage
                );
                log.debug("Typing indicator sent to: {}", indicator.getReceiverId());
            } else {
                log.debug("User {} is offline, typing indicator not sent", indicator.getReceiverId());
            }

        } catch (Exception e) {
            log.error("Error handling typing indicator from {}: {}",
                    authentication.getName(), e.getMessage(), e);
        }
    }

    @MessageMapping("/chat/stop-typing")
    public void handleStopTyping(@Payload TypingIndicator indicator, Authentication authentication) {
        try {
            log.debug("Stop typing from: {} to: {} for delivery: {}",
                    authentication.getName(), indicator.getReceiverId(), indicator.getDeliveryId());

            WebSocketMessage wsMessage = new WebSocketMessage(
                    WebSocketMessage.WebSocketMessageType.TYPING_STOP,
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
                log.debug("Stop typing sent to: {}", indicator.getReceiverId());
            }

        } catch (Exception e) {
            log.error("Error handling stop typing from {}: {}",
                    authentication.getName(), e.getMessage(), e);
        }
    }

    // **اضافة جديدة** - Method للتأكد من الـ message delivery
    @MessageMapping("/chat/ping")
    public void handlePing(@Payload Map<String, Object> payload, Authentication authentication) {
        try {
            log.debug("Ping received from user: {}", authentication.getName());

            Map<String, Object> pongResponse = Map.of(
                    "type", "PONG",
                    "userId", authentication.getName(),
                    "timestamp", System.currentTimeMillis(),
                    "status", "OK"
            );

            messagingTemplate.convertAndSendToUser(
                    authentication.getName(),
                    "/queue/ping",
                    pongResponse
            );

        } catch (Exception e) {
            log.error("Error handling ping from {}: {}", authentication.getName(), e.getMessage());
        }
    }

    // **اضافة جديدة** - Test method لتجريب الـ WebSocket
    @MessageMapping("/chat/test")
    public void handleTest(@Payload Map<String, String> payload, Authentication authentication) {
        try {
            String receiverId = payload.get("receiverId");
            String testMessage = payload.getOrDefault("message", "Test message from WebSocket");

            log.info("Test message from: {} to: {} - {}",
                    authentication.getName(), receiverId, testMessage);

            Map<String, Object> testResponse = Map.of(
                    "type", "TEST_RESPONSE",
                    "from", authentication.getName(),
                    "message", testMessage,
                    "timestamp", System.currentTimeMillis()
            );

            // Send to receiver
            if (receiverId != null && !receiverId.isEmpty()) {
                messagingTemplate.convertAndSendToUser(
                        receiverId,
                        "/queue/messages",
                        testResponse
                );
                log.info("Test message sent to receiver: {}", receiverId);
            }

            // Send confirmation to sender
            messagingTemplate.convertAndSendToUser(
                    authentication.getName(),
                    "/queue/messages",
                    Map.of(
                            "type", "TEST_CONFIRMATION",
                            "status", "Test message sent successfully",
                            "targetUser", receiverId
                    )
            );

        } catch (Exception e) {
            log.error("Error handling test message: {}", e.getMessage(), e);
        }
    }

    // **اضافة جديدة** - Method للتحقق من الـ connection status
    @MessageMapping("/chat/status")
    public void handleStatusCheck(Authentication authentication) {
        try {
            Map<String, Object> statusResponse = Map.of(
                    "userId", authentication.getName(),
                    "status", "CONNECTED",
                    "timestamp", System.currentTimeMillis(),
                    "authorities", authentication.getAuthorities().stream()
                            .map(Object::toString)
                            .toList()
            );

            messagingTemplate.convertAndSendToUser(
                    authentication.getName(),
                    "/queue/status",
                    statusResponse
            );

            log.debug("Status check response sent to: {}", authentication.getName());

        } catch (Exception e) {
            log.error("Error handling status check: {}", e.getMessage());
        }
    }
}