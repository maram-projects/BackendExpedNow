package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.*;
import com.example.ExpedNow.services.core.impl.ChatService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:4200", "https://your-domain.com"})
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    @PostMapping("/send")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'INDIVIDUAL', 'ENTERPRISE', 'DELIVERY_PERSON', 'PROFESSIONAL', 'TEMPORARY')")
    public ResponseEntity<MessageDTO> sendMessage(
            @Valid @RequestBody ChatMessageRequest request,
            Authentication authentication) {
        try {
            log.info("Sending message from user: {} to user: {}",
                    authentication.getName(), request.getReceiverId());
            MessageDTO message = chatService.sendMessage(request, authentication);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("Error sending message", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/messages")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'INDIVIDUAL', 'ENTERPRISE', 'DELIVERY_PERSON', 'PROFESSIONAL', 'TEMPORARY')")
    public ResponseEntity<Page<MessageDTO>> getMessages(
            @RequestParam String deliveryId,
            @RequestParam String otherUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        try {
            log.info("Getting messages for delivery: {} between users: {} and {}",
                    deliveryId, authentication.getName(), otherUserId);
            Page<MessageDTO> messages = chatService.getMessages(deliveryId, otherUserId, page, size, authentication);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error getting messages", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // FIXED: Added proper @PreAuthorize annotation
    @GetMapping("/rooms")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'INDIVIDUAL', 'ENTERPRISE', 'DELIVERY_PERSON', 'PROFESSIONAL', 'TEMPORARY', 'ADMIN')")
    public ResponseEntity<?> getChatRooms(Authentication authentication) {
        try {
            log.info("Getting chat rooms for user: {}", authentication.getName());

            // Add null check for authentication
            if (authentication == null || authentication.getName() == null) {
                log.error("Authentication is null or invalid");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Authentication required"));
            }

            List<ChatRoomDTO> chatRooms = chatService.getUserChatRooms(authentication);
            log.info("Successfully retrieved {} chat rooms for user: {}",
                    chatRooms.size(), authentication.getName());
            return ResponseEntity.ok(chatRooms);
        } catch (Exception e) {
            log.error("Failed to get chat rooms for user: {}",
                    authentication != null ? authentication.getName() : "unknown", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to load chat rooms",
                            "message", e.getMessage(),
                            "timestamp", System.currentTimeMillis()
                    ));
        }
    }

    @PostMapping("/mark-read")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'INDIVIDUAL', 'ENTERPRISE', 'DELIVERY_PERSON', 'PROFESSIONAL', 'TEMPORARY')")
    public ResponseEntity<Void> markMessagesAsRead(
            @RequestParam String deliveryId,
            @RequestParam String senderId,
            Authentication authentication) {
        try {
            log.info("Marking messages as read for delivery: {} from sender: {}",
                    deliveryId, senderId);
            chatService.markMessagesAsRead(deliveryId, senderId, authentication);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error marking messages as read", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'INDIVIDUAL', 'ENTERPRISE', 'DELIVERY_PERSON', 'PROFESSIONAL', 'TEMPORARY')")
    public ResponseEntity<Long> getUnreadCount(
            @RequestParam String deliveryId,
            Authentication authentication) {
        try {
            log.info("Getting unread count for delivery: {}", deliveryId);
            long count = chatService.getUnreadMessageCount(deliveryId, authentication);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            log.error("Error getting unread count", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Add a health check endpoint for testing
    @GetMapping("/health")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'INDIVIDUAL', 'ENTERPRISE', 'DELIVERY_PERSON', 'PROFESSIONAL', 'TEMPORARY', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> healthCheck(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "user", authentication.getName(),
                "authorities", authentication.getAuthorities(),
                "timestamp", System.currentTimeMillis()
        ));
    }
}