package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.*;
import com.example.ExpedNow.services.core.impl.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/send")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'INDIVIDUAL', 'ENTERPRISE', 'DELIVERY_PERSON', 'PROFESSIONAL', 'TEMPORARY')")
    public ResponseEntity<MessageDTO> sendMessage(
            @Valid @RequestBody ChatMessageRequest request,
            Authentication authentication) {
        try {
            MessageDTO message = chatService.sendMessage(request, authentication);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
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
            Page<MessageDTO> messages = chatService.getMessages(deliveryId, otherUserId, page, size, authentication);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/rooms")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'INDIVIDUAL', 'ENTERPRISE', 'DELIVERY_PERSON', 'PROFESSIONAL', 'TEMPORARY')")
    public ResponseEntity<List<ChatRoomDTO>> getChatRooms(Authentication authentication) {
        try {
            List<ChatRoomDTO> chatRooms = chatService.getUserChatRooms(authentication);
            return ResponseEntity.ok(chatRooms);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/mark-read")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'INDIVIDUAL', 'ENTERPRISE', 'DELIVERY_PERSON', 'PROFESSIONAL', 'TEMPORARY')")
    public ResponseEntity<Void> markMessagesAsRead(
            @RequestParam String deliveryId,
            @RequestParam String senderId,
            Authentication authentication) {
        try {
            chatService.markMessagesAsRead(deliveryId, senderId, authentication);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'INDIVIDUAL', 'ENTERPRISE', 'DELIVERY_PERSON', 'PROFESSIONAL', 'TEMPORARY')")
    public ResponseEntity<Long> getUnreadCount(
            @RequestParam String deliveryId,
            Authentication authentication) {
        try {
            long count = chatService.getUnreadMessageCount(deliveryId, authentication);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
