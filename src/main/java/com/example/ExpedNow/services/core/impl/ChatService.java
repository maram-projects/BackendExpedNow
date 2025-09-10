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
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Validated
public class ChatService {

    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public MessageDTO sendMessage(@Valid ChatMessageRequest request, Authentication authentication) {
        try {
            // Extract user details
            CustomUserDetailsService.CustomUserDetails userDetails = getUserDetails(authentication);
            String senderId = userDetails.getUserId();

            // Validate attachment if present
            if (!request.isAttachmentValid()) {
                throw new IllegalArgumentException("Attachment URL and type must both be provided or both be null");
            }

            // Validate users exist
            User sender = userRepository.findById(senderId)
                    .orElseThrow(() -> new RuntimeException("Sender not found with ID: " + senderId));
            User receiver = userRepository.findById(request.getReceiverId())
                    .orElseThrow(() -> new RuntimeException("Receiver not found with ID: " + request.getReceiverId()));

            // Create or get chat room
            ChatRoom chatRoom = getOrCreateChatRoom(request.getDeliveryId(), senderId, request.getReceiverId());

            // Create and save message
            Message message = Message.builder()
                    .senderId(senderId)
                    .receiverId(request.getReceiverId())
                    .deliveryId(request.getDeliveryId())
                    .content(request.getContent().trim())
                    .type(request.getType())
                    .status(Message.MessageStatus.SENT)
                    .attachmentUrl(request.getAttachmentUrl())
                    .attachmentType(request.getAttachmentType())
                    .timestamp(LocalDateTime.now())
                    .build();

            Message savedMessage = messageRepository.save(message);

            // Update chat room with last message info
            updateChatRoomLastMessage(chatRoom, savedMessage);

            // Convert to DTO
            MessageDTO messageDTO = convertToMessageDTO(savedMessage, sender);

            // Send WebSocket notifications
            sendWebSocketNotifications(messageDTO);

            log.info("Message sent successfully from {} to {} for delivery {}",
                    senderId, request.getReceiverId(), request.getDeliveryId());
            return messageDTO;

        } catch (Exception e) {
            log.error("Error sending message from {} to {}: {}",
                    authentication.getName(), request.getReceiverId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send message: " + e.getMessage(), e);
        }
    }

    public Page<MessageDTO> getMessages(String deliveryId, String otherUserId, int page, int size,
                                        Authentication authentication) {
        try {
            CustomUserDetailsService.CustomUserDetails userDetails = getUserDetails(authentication);
            String currentUserId = userDetails.getUserId();

            // Validate parameters
            if (page < 0) page = 0;
            if (size <= 0 || size > 100) size = 20; // Limit max page size

            Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());

            // Get messages between users for specific delivery
            Page<Message> messages = messageRepository.findMessagesBetweenUsersForDelivery(
                    currentUserId, otherUserId, deliveryId, pageable);

            // Convert to DTOs with sender information
            return messages.map(message -> {
                User sender = userRepository.findById(message.getSenderId()).orElse(null);
                return convertToMessageDTO(message, sender);
            });

        } catch (Exception e) {
            log.error("Error retrieving messages for delivery {} between users {} and {}: {}",
                    deliveryId, authentication.getName(), otherUserId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve messages: " + e.getMessage(), e);
        }
    }

    public List<ChatRoomDTO> getUserChatRooms(Authentication authentication) {
        try {
            CustomUserDetailsService.CustomUserDetails userDetails = getUserDetails(authentication);
            String userId = userDetails.getUserId();

            log.info("Retrieving chat rooms for user: {}", userId);

            // Get all user chat rooms (both as client and delivery person)
            List<ChatRoom> chatRooms = chatRoomRepository.findAllUserChatRooms(userId);
            log.info("Found {} chat rooms for user: {}", chatRooms.size(), userId);

            // Convert to DTOs and sort by last message date
            List<ChatRoomDTO> result = chatRooms.stream()
                    .map(room -> convertToChatRoomDTO(room, userId))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(
                            ChatRoomDTO::getLastMessageAt,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    ))
                    .collect(Collectors.toList());

            log.info("Successfully converted {} chat rooms to DTOs for user: {}", result.size(), userId);
            return result;

        } catch (Exception e) {
            log.error("Error retrieving chat rooms for user {}: {}", authentication.getName(), e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve chat rooms: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void markMessagesAsRead(String deliveryId, String senderId, Authentication authentication) {
        try {
            CustomUserDetailsService.CustomUserDetails userDetails = getUserDetails(authentication);
            String currentUserId = userDetails.getUserId();

            // Find unread messages more efficiently
            List<Message> unreadMessages = messageRepository.findUnreadMessagesBetweenUsers(
                    senderId, currentUserId, deliveryId);

            if (!unreadMessages.isEmpty()) {
                LocalDateTime readTime = LocalDateTime.now();

                // Mark messages as read
                unreadMessages.forEach(message -> {
                    message.setStatus(Message.MessageStatus.READ);
                    message.setReadAt(readTime);
                });

                messageRepository.saveAll(unreadMessages);

                // Send read receipt notification
                sendReadReceiptNotification(senderId, deliveryId, unreadMessages.size(), readTime, currentUserId);

                log.info("Marked {} messages as read for delivery {} from sender {}",
                        unreadMessages.size(), deliveryId, senderId);
            }
        } catch (Exception e) {
            log.error("Error marking messages as read for delivery {} from sender {}: {}",
                    deliveryId, senderId, e.getMessage(), e);
            throw new RuntimeException("Failed to mark messages as read: " + e.getMessage(), e);
        }
    }

    public long getUnreadMessageCount(String deliveryId, Authentication authentication) {
        try {
            CustomUserDetailsService.CustomUserDetails userDetails = getUserDetails(authentication);
            String userId = userDetails.getUserId();

            return messageRepository.countByDeliveryIdAndReceiverIdAndStatusNot(
                    deliveryId, userId, Message.MessageStatus.READ);

        } catch (Exception e) {
            log.error("Error getting unread message count for delivery {} and user {}: {}",
                    deliveryId, authentication.getName(), e.getMessage(), e);
            return 0;
        }
    }

    @Transactional
    public ChatRoom getOrCreateChatRoom(String deliveryId, String senderId, String receiverId) {
        try {
            log.info("Getting or creating chat room for delivery: {} between users: {} and {}",
                    deliveryId, senderId, receiverId);

            // Look for existing room
            Optional<ChatRoom> existingRoom = chatRoomRepository.findByDeliveryIdAndParticipants(
                    deliveryId, senderId, receiverId);

            if (existingRoom.isPresent()) {
                ChatRoom room = existingRoom.get();

                // Reactivate if inactive
                if (!room.isActive()) {
                    room.setActive(true);
                    room.setUpdatedAt(LocalDateTime.now());
                    room = chatRoomRepository.save(room);
                    log.info("Reactivated chat room: {}", room.getId());
                }

                log.info("Using existing chat room: {}", room.getId());
                return room;
            }

            // Create new chat room
            return createNewChatRoom(deliveryId, senderId, receiverId);

        } catch (Exception e) {
            log.error("Error getting/creating chat room for delivery {}: {}", deliveryId, e.getMessage(), e);
            throw new RuntimeException("Failed to get/create chat room: " + e.getMessage(), e);
        }
    }

    // Private helper methods

    private CustomUserDetailsService.CustomUserDetails getUserDetails(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalArgumentException("Authentication is required");
        }
        return (CustomUserDetailsService.CustomUserDetails) authentication.getPrincipal();
    }

    private ChatRoom createNewChatRoom(String deliveryId, String senderId, String receiverId) {
        log.info("Creating new chat room for delivery: {}", deliveryId);

        // Determine user roles
        String clientId = isClient(senderId) ? senderId : receiverId;
        String deliveryPersonId = isDeliveryPerson(senderId) ? senderId : receiverId;

        // Create participants list
        List<String> participants = Arrays.asList(senderId, receiverId);

        // Build new chat room
        ChatRoom newRoom = ChatRoom.builder()
                .id(generateChatRoomId(deliveryId, clientId, deliveryPersonId))
                .deliveryId(deliveryId)
                .clientId(clientId)
                .deliveryPersonId(deliveryPersonId)
                .participants(participants)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ChatRoom savedRoom = chatRoomRepository.save(newRoom);
        log.info("Created new chat room: {}", savedRoom.getId());
        return savedRoom;
    }

    private void updateChatRoomLastMessage(ChatRoom chatRoom, Message message) {
        chatRoom.setLastMessageAt(message.getTimestamp());
        chatRoom.setLastMessageContent(truncateContent(message.getContent()));
        chatRoom.setUpdatedAt(LocalDateTime.now());
        chatRoomRepository.save(chatRoom);
    }

    private String truncateContent(String content) {
        if (content == null) return null;
        return content.length() > 100 ? content.substring(0, 97) + "..." : content;
    }

    private void sendWebSocketNotifications(MessageDTO messageDTO) {
        try {
            WebSocketMessage wsMessage = new WebSocketMessage(
                    WebSocketMessage.WebSocketMessageType.CHAT_MESSAGE,
                    messageDTO,
                    messageDTO.getReceiverId(),
                    messageDTO.getDeliveryId()
            );

            // Send to receiver's personal queue
            messagingTemplate.convertAndSendToUser(messageDTO.getReceiverId(), "/queue/messages", wsMessage);

            // Send to sender for confirmation
            messagingTemplate.convertAndSendToUser(messageDTO.getSenderId(), "/queue/messages", wsMessage);

            // Broadcast to delivery-specific topic
            messagingTemplate.convertAndSend("/topic/delivery." + messageDTO.getDeliveryId(), wsMessage);

            log.debug("WebSocket notifications sent for message to delivery: {}", messageDTO.getDeliveryId());
        } catch (Exception e) {
            log.error("Failed to send WebSocket notifications: {}", e.getMessage(), e);
        }
    }

    private void sendReadReceiptNotification(String senderId, String deliveryId, int readCount,
                                             LocalDateTime readTime, String readBy) {
        try {
            Map<String, Object> readReceipt = Map.of(
                    "type", "MESSAGE_READ",
                    "deliveryId", deliveryId,
                    "readCount", readCount,
                    "readBy", readBy,
                    "readAt", readTime
            );

            WebSocketMessage wsMessage = new WebSocketMessage(
                    WebSocketMessage.WebSocketMessageType.MESSAGE_READ,
                    readReceipt,
                    senderId,
                    deliveryId
            );

            messagingTemplate.convertAndSendToUser(senderId, "/queue/messages", wsMessage);
            log.debug("Read receipt sent to sender: {} for delivery: {}", senderId, deliveryId);
        } catch (Exception e) {
            log.error("Failed to send read receipt notification: {}", e.getMessage(), e);
        }
    }

    private String generateChatRoomId(String deliveryId, String clientId, String deliveryPersonId) {
        String combined = clientId + deliveryPersonId;
        String hash = String.valueOf(Math.abs(combined.hashCode()));
        return "chat_" + deliveryId + "_" + hash;
    }

    private boolean isClient(String userId) {
        try {
            return userRepository.findById(userId)
                    .map(user -> user.getRoles().stream()
                            .anyMatch(role -> {
                                String roleName = role.name().toUpperCase();
                                return "CLIENT".equals(roleName) ||
                                        "INDIVIDUAL".equals(roleName) ||
                                        "ENTERPRISE".equals(roleName);
                            }))
                    .orElse(false);
        } catch (Exception e) {
            log.error("Error checking if user {} is client: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    private boolean isDeliveryPerson(String userId) {
        try {
            return userRepository.findById(userId)
                    .map(user -> user.getRoles().stream()
                            .anyMatch(role -> {
                                String roleName = role.name().toUpperCase();
                                return "DELIVERY_PERSON".equals(roleName) ||
                                        "PROFESSIONAL".equals(roleName) ||
                                        "TEMPORARY".equals(roleName);
                            }))
                    .orElse(false);
        } catch (Exception e) {
            log.error("Error checking if user {} is delivery person: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    private MessageDTO convertToMessageDTO(Message message, User sender) {
        try {
            MessageDTO dto = new MessageDTO();
            dto.setId(message.getId());
            dto.setSenderId(message.getSenderId());
            dto.setSenderName(sender != null ? sender.getFullName() : "Unknown User");
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
        } catch (Exception e) {
            log.error("Error converting message {} to DTO: {}", message.getId(), e.getMessage(), e);
            return null;
        }
    }

    private ChatRoomDTO convertToChatRoomDTO(ChatRoom room, String currentUserId) {
        try {
            if (room == null) {
                log.warn("Attempting to convert null ChatRoom to DTO");
                return null;
            }

            ChatRoomDTO dto = new ChatRoomDTO();
            dto.setId(room.getId());
            dto.setDeliveryId(room.getDeliveryId());
            dto.setClientId(room.getClientId());
            dto.setDeliveryPersonId(room.getDeliveryPersonId());
            dto.setCreatedAt(room.getCreatedAt());
            dto.setLastMessageAt(room.getLastMessageAt());
            dto.setLastMessageContent(room.getLastMessageContent());
            dto.setActive(room.isActive());

            // Set user names safely
            dto.setClientName(getUserName(room.getClientId()).orElse("Unknown Client"));
            dto.setDeliveryPersonName(getUserName(room.getDeliveryPersonId()).orElse("Unknown Delivery Person"));

            // Set unread count
            dto.setUnreadCount(getUnreadCountForRoom(room.getDeliveryId(), currentUserId));

            return dto;
        } catch (Exception e) {
            log.error("Error converting ChatRoom {} to DTO: {}",
                    room != null ? room.getId() : "null", e.getMessage(), e);
            return null;
        }
    }

    private Optional<String> getUserName(String userId) {
        if (userId == null) return Optional.empty();

        try {
            return userRepository.findById(userId)
                    .map(User::getFullName);
        } catch (Exception e) {
            log.error("Error finding user name for ID {}: {}", userId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private int getUnreadCountForRoom(String deliveryId, String currentUserId) {
        try {
            return (int) messageRepository.countByDeliveryIdAndReceiverIdAndStatusNot(
                    deliveryId, currentUserId, Message.MessageStatus.READ);
        } catch (Exception e) {
            log.error("Error counting unread messages for delivery {} and user {}: {}",
                    deliveryId, currentUserId, e.getMessage(), e);
            return 0;
        }
    }
}