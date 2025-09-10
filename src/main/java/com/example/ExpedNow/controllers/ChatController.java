package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.*;
import com.example.ExpedNow.services.core.impl.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:4200", "https://your-domain.com"})
@Slf4j
public class ChatController {

    private final ChatService chatService;

    private static final String CHAT_AUTHORITIES = "hasAnyAuthority('CLIENT', 'INDIVIDUAL', 'ENTERPRISE', " +
            "'DELIVERY_PERSON', 'PROFESSIONAL', 'TEMPORARY', 'ADMIN')";

    @PostMapping("/send")
    @PreAuthorize(CHAT_AUTHORITIES)
    public ResponseEntity<?> sendMessage(
            @Valid @RequestBody ChatMessageRequest request,
            BindingResult bindingResult,
            Authentication authentication) {

        log.info("Sending message request from user: {} to user: {} for delivery: {}",
                authentication.getName(), request.getReceiverId(), request.getDeliveryId());

        try {
            // Check for validation errors
            if (bindingResult.hasErrors()) {
                Map<String, String> errors = bindingResult.getFieldErrors().stream()
                        .collect(Collectors.toMap(
                                error -> error.getField(),
                                error -> error.getDefaultMessage(),
                                (existing, replacement) -> existing
                        ));

                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Validation failed",
                        "details", errors,
                        "timestamp", LocalDateTime.now()
                ));
            }

            // Additional custom validation
            if (!request.isAttachmentValid()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid attachment",
                        "message", "Attachment URL and type must both be provided or both be null",
                        "timestamp", LocalDateTime.now()
                ));
            }

            MessageDTO message = chatService.sendMessage(request, authentication);

            log.info("Message sent successfully with ID: {} from user: {} to user: {}",
                    message.getId(), authentication.getName(), request.getReceiverId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Message sent successfully",
                    "data", message,
                    "timestamp", LocalDateTime.now()
            ));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid message request from user {}: {}", authentication.getName(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid request",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Error sending message from user {}: {}", authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Failed to send message",
                    "message", "An unexpected error occurred. Please try again.",
                    "timestamp", LocalDateTime.now()
            ));
        }
    }

    @GetMapping("/messages")
    @PreAuthorize(CHAT_AUTHORITIES)
    public ResponseEntity<?> getMessages(
            @RequestParam @NotBlank(message = "Delivery ID is required") String deliveryId,
            @RequestParam @NotBlank(message = "Other user ID is required") String otherUserId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            Authentication authentication) {

        log.info("Getting messages for delivery: {} between users: {} and {}, page: {}, size: {}",
                deliveryId, authentication.getName(), otherUserId, page, size);

        try {
            // Validate page size limits
            if (size > 100) {
                size = 100; // Maximum page size limit
                log.warn("Page size limited to 100 for user: {}", authentication.getName());
            }

            Page<MessageDTO> messages = chatService.getMessages(deliveryId, otherUserId, page, size, authentication);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", messages.getContent());
            response.put("pagination", Map.of(
                    "page", messages.getNumber(),
                    "size", messages.getSize(),
                    "totalElements", messages.getTotalElements(),
                    "totalPages", messages.getTotalPages(),
                    "first", messages.isFirst(),
                    "last", messages.isLast(),
                    "hasNext", messages.hasNext(),
                    "hasPrevious", messages.hasPrevious()
            ));
            response.put("timestamp", LocalDateTime.now());

            log.info("Retrieved {} messages for delivery: {} between users: {} and {}",
                    messages.getNumberOfElements(), deliveryId, authentication.getName(), otherUserId);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid message retrieval request from user {}: {}", authentication.getName(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid request",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Error retrieving messages for user {}: {}", authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Failed to retrieve messages",
                    "message", "An unexpected error occurred. Please try again.",
                    "timestamp", LocalDateTime.now()
            ));
        }
    }

    @GetMapping("/rooms")
    @PreAuthorize(CHAT_AUTHORITIES)
    public ResponseEntity<?> getChatRooms(Authentication authentication) {
        log.info("Getting chat rooms for user: {}", authentication.getName());

        try {
            // Validate authentication
            if (authentication == null || authentication.getName() == null) {
                log.error("Authentication is null or invalid");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "error", "Authentication required",
                        "message", "Valid authentication is required to access chat rooms",
                        "timestamp", LocalDateTime.now()
                ));
            }

            List<ChatRoomDTO> chatRooms = chatService.getUserChatRooms(authentication);

            log.info("Successfully retrieved {} chat rooms for user: {}", chatRooms.size(), authentication.getName());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", String.format("Retrieved %d chat rooms", chatRooms.size()),
                    "data", chatRooms,
                    "count", chatRooms.size(),
                    "timestamp", LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("Failed to get chat rooms for user: {}: {}",
                    authentication != null ? authentication.getName() : "unknown", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Failed to load chat rooms",
                    "message", "An unexpected error occurred while loading your chat rooms. Please try again.",
                    "timestamp", LocalDateTime.now()
            ));
        }
    }

    @PostMapping("/mark-read")
    @PreAuthorize(CHAT_AUTHORITIES)
    public ResponseEntity<?> markMessagesAsRead(
            @RequestParam @NotBlank(message = "Delivery ID is required") String deliveryId,
            @RequestParam @NotBlank(message = "Sender ID is required") String senderId,
            Authentication authentication) {

        log.info("Marking messages as read for delivery: {} from sender: {} by user: {}",
                deliveryId, senderId, authentication.getName());

        try {
            chatService.markMessagesAsRead(deliveryId, senderId, authentication);

            log.info("Messages marked as read successfully for delivery: {} from sender: {}",
                    deliveryId, senderId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Messages marked as read successfully",
                    "timestamp", LocalDateTime.now()
            ));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid mark-as-read request from user {}: {}", authentication.getName(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid request",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Error marking messages as read for user {}: {}", authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Failed to mark messages as read",
                    "message", "An unexpected error occurred. Please try again.",
                    "timestamp", LocalDateTime.now()
            ));
        }
    }

    @GetMapping("/unread-count")
    @PreAuthorize(CHAT_AUTHORITIES)
    public ResponseEntity<?> getUnreadCount(
            @RequestParam @NotBlank(message = "Delivery ID is required") String deliveryId,
            Authentication authentication) {

        log.info("Getting unread count for delivery: {} and user: {}", deliveryId, authentication.getName());

        try {
            long count = chatService.getUnreadMessageCount(deliveryId, authentication);

            log.info("Retrieved unread count: {} for delivery: {} and user: {}",
                    count, deliveryId, authentication.getName());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "deliveryId", deliveryId,
                            "unreadCount", count
                    ),
                    "timestamp", LocalDateTime.now()
            ));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid unread count request from user {}: {}", authentication.getName(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid request",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Error getting unread count for user {}: {}", authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Failed to get unread count",
                    "message", "An unexpected error occurred. Please try again.",
                    "count", 0,
                    "timestamp", LocalDateTime.now()
            ));
        }
    }

    @GetMapping("/health")
    @PreAuthorize(CHAT_AUTHORITIES)
    public ResponseEntity<Map<String, Object>> healthCheck(Authentication authentication) {
        log.debug("Health check requested by user: {}", authentication.getName());

        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "service", "Chat Service",
                "user", authentication.getName(),
                "authorities", authentication.getAuthorities().stream()
                        .map(Object::toString)
                        .collect(Collectors.toList()),
                "timestamp", LocalDateTime.now(),
                "version", "1.0"
        ));
    }

    @GetMapping("/room/{deliveryId}/participants")
    @PreAuthorize(CHAT_AUTHORITIES)
    public ResponseEntity<?> getChatRoomParticipants(
            @PathVariable @NotBlank String deliveryId,
            Authentication authentication) {

        log.info("Getting chat room participants for delivery: {} requested by user: {}",
                deliveryId, authentication.getName());

        try {
            // This would require additional service method to get participants
            // For now, return a placeholder response
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Feature not yet implemented",
                    "deliveryId", deliveryId,
                    "timestamp", LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("Error getting participants for delivery {}: {}", deliveryId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Failed to get participants",
                    "message", "An unexpected error occurred. Please try again.",
                    "timestamp", LocalDateTime.now()
            ));
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Invalid request: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid request",
                "message", e.getMessage(),
                "timestamp", LocalDateTime.now()
        ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("Runtime error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error",
                "message", "An unexpected error occurred. Please try again.",
                "timestamp", LocalDateTime.now()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Unexpected error",
                "message", "An unexpected error occurred. Please contact support if this persists.",
                "timestamp", LocalDateTime.now()
        ));
    }
}