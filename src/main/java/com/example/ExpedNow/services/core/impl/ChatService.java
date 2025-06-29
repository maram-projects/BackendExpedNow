package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.dto.*;
import com.example.ExpedNow.models.*;
import com.example.ExpedNow.repositories.*;
import com.example.ExpedNow.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public MessageDTO sendMessage(ChatMessageRequest request, Authentication authentication) {
        try {
            CustomUserDetailsService.CustomUserDetails userDetails =
                    (CustomUserDetailsService.CustomUserDetails) authentication.getPrincipal();
            String senderId = userDetails.getUserId();

            // Validate users exist
            User sender = userRepository.findById(senderId)
                    .orElseThrow(() -> new RuntimeException("Sender not found"));
            User receiver = userRepository.findById(request.getReceiverId())
                    .orElseThrow(() -> new RuntimeException("Receiver not found"));

            // Create or get chat room
            ChatRoom chatRoom = getOrCreateChatRoom(request.getDeliveryId(), senderId, request.getReceiverId());

            // Create message
            Message message = Message.builder()
                    .senderId(senderId)
                    .receiverId(request.getReceiverId())
                    .deliveryId(request.getDeliveryId())
                    .content(request.getContent())
                    .type(request.getType() != null ? request.getType() : Message.MessageType.TEXT)
                    .status(Message.MessageStatus.SENT)
                    .attachmentUrl(request.getAttachmentUrl())
                    .attachmentType(request.getAttachmentType())
                    .build();

            Message savedMessage = messageRepository.save(message);

            // Update chat room
            chatRoom.setLastMessageAt(savedMessage.getTimestamp());
            chatRoom.setLastMessageContent(savedMessage.getContent());
            chatRoomRepository.save(chatRoom);

            // Convert to DTO
            MessageDTO messageDTO = convertToMessageDTO(savedMessage, sender);

            // Send via WebSocket
            sendWebSocketMessage(messageDTO, request.getReceiverId());

            log.info("Message sent from {} to {} for delivery {}", senderId, request.getReceiverId(), request.getDeliveryId());
            return messageDTO;

        } catch (Exception e) {
            log.error("Error sending message", e);
            throw new RuntimeException("Failed to send message: " + e.getMessage());
        }
    }

    public Page<MessageDTO> getMessages(String deliveryId, String otherUserId, int page, int size, Authentication authentication) {
        CustomUserDetailsService.CustomUserDetails userDetails =
                (CustomUserDetailsService.CustomUserDetails) authentication.getPrincipal();
        String currentUserId = userDetails.getUserId();

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<Message> messages = messageRepository.findMessagesBetweenUsersForDelivery(
                currentUserId, otherUserId, deliveryId, pageable);

        return messages.map(message -> {
            User sender = userRepository.findById(message.getSenderId()).orElse(null);
            return convertToMessageDTO(message, sender);
        });
    }

    public List<ChatRoomDTO> getUserChatRooms(Authentication authentication) {
        CustomUserDetailsService.CustomUserDetails userDetails =
                (CustomUserDetailsService.CustomUserDetails) authentication.getPrincipal();
        String userId = userDetails.getUserId();

        List<ChatRoom> chatRooms = chatRoomRepository.findUserChatRooms(userId);

        return chatRooms.stream()
                .map(room -> convertToChatRoomDTO(room, userId))
                .sorted(Comparator.comparing(ChatRoomDTO::getLastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Transactional
    public void markMessagesAsRead(String deliveryId, String senderId, Authentication authentication) {
        CustomUserDetailsService.CustomUserDetails userDetails =
                (CustomUserDetailsService.CustomUserDetails) authentication.getPrincipal();
        String currentUserId = userDetails.getUserId();

        List<Message> unreadMessages = messageRepository.findMessagesBetweenUsersForDelivery(
                        senderId, currentUserId, deliveryId, Pageable.unpaged()).getContent()
                .stream()
                .filter(msg -> msg.getStatus() != Message.MessageStatus.READ && msg.getReceiverId().equals(currentUserId))
                .collect(Collectors.toList());

        LocalDateTime now = LocalDateTime.now();
        unreadMessages.forEach(message -> {
            message.setStatus(Message.MessageStatus.READ);
            message.setReadAt(now);
        });

        messageRepository.saveAll(unreadMessages);

        // Notify sender via WebSocket
        if (!unreadMessages.isEmpty()) {
            WebSocketMessage wsMessage = new WebSocketMessage(
                    WebSocketMessage.WebSocketMessageType.MESSAGE_READ,
                    Map.of("deliveryId", deliveryId, "readCount", unreadMessages.size()),
                    senderId,
                    deliveryId
            );
            messagingTemplate.convertAndSendToUser(senderId, "/queue/messages", wsMessage);
        }
    }

    public long getUnreadMessageCount(String deliveryId, Authentication authentication) {
        CustomUserDetailsService.CustomUserDetails userDetails =
                (CustomUserDetailsService.CustomUserDetails) authentication.getPrincipal();
        String userId = userDetails.getUserId();

        return messageRepository.countUnreadMessagesForDelivery(deliveryId, userId);
    }

    private ChatRoom getOrCreateChatRoom(String deliveryId, String senderId, String receiverId) {
        return chatRoomRepository.findByDeliveryId(deliveryId)
                .orElseGet(() -> {
                    ChatRoom newRoom = ChatRoom.builder()
                            .id(ChatRoom.generateChatRoomId(deliveryId))
                            .deliveryId(deliveryId)
                            .clientId(isClient(senderId) ? senderId : receiverId)
                            .deliveryPersonId(isDeliveryPerson(senderId) ? senderId : receiverId)
                            .participants(Set.of(senderId, receiverId))
                            .isActive(true)
                            .build();
                    return chatRoomRepository.save(newRoom);
                });
    }

    private boolean isClient(String userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRoles().stream()
                        .anyMatch(role -> role.name().contains("CLIENT") ||
                                role.name().contains("INDIVIDUAL") ||
                                role.name().contains("ENTERPRISE")))
                .orElse(false);
    }

    private boolean isDeliveryPerson(String userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRoles().stream()
                        .anyMatch(role -> role.name().contains("DELIVERY") ||
                                role.name().contains("PROFESSIONAL") ||
                                role.name().contains("TEMPORARY")))
                .orElse(false);
    }

    private void sendWebSocketMessage(MessageDTO messageDTO, String receiverId) {
        try {
            WebSocketMessage wsMessage = new WebSocketMessage(
                    WebSocketMessage.WebSocketMessageType.CHAT_MESSAGE,
                    messageDTO,
                    receiverId,
                    messageDTO.getDeliveryId()
            );
            messagingTemplate.convertAndSendToUser(receiverId, "/queue/messages", wsMessage);
        } catch (Exception e) {
            log.error("Failed to send WebSocket message to user {}", receiverId, e);
        }
    }

    private MessageDTO convertToMessageDTO(Message message, User sender) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setSenderId(message.getSenderId());
        dto.setSenderName(sender != null ? sender.getFullName() : "Unknown");
        dto.setReceiverId(message.getReceiverId());
        dto.setDeliveryId(message.getDeliveryId());
        dto.setContent(message.getContent());
        dto.setType(message.getType());
        dto.setStatus(message.getStatus());
        dto.setTimestamp(message.getTimestamp());
        dto.setReadAt(message.getReadAt());
        dto.setAttachmentUrl(message.getAttachmentUrl());
        dto.setAttachmentType(message.getAttachmentType());
        return dto;
    }

    private ChatRoomDTO convertToChatRoomDTO(ChatRoom room, String currentUserId) {
        ChatRoomDTO dto = new ChatRoomDTO();
        dto.setId(room.getId());
        dto.setDeliveryId(room.getDeliveryId());
        dto.setClientId(room.getClientId());
        dto.setDeliveryPersonId(room.getDeliveryPersonId());
        dto.setCreatedAt(room.getCreatedAt());
        dto.setLastMessageAt(room.getLastMessageAt());
        dto.setLastMessageContent(room.getLastMessageContent());
        dto.setActive(room.isActive());

        // Set participant names
        if (room.getClientId() != null) {
            userRepository.findById(room.getClientId())
                    .ifPresent(client -> dto.setClientName(client.getFullName()));
        }
        if (room.getDeliveryPersonId() != null) {
            userRepository.findById(room.getDeliveryPersonId())
                    .ifPresent(deliveryPerson -> dto.setDeliveryPersonName(deliveryPerson.getFullName()));
        }

        // Count unread messages
        dto.setUnreadCount((int) messageRepository.countUnreadMessagesForDelivery(room.getDeliveryId(), currentUserId));

        return dto;
    }
}