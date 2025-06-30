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
        try {
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
        } catch (Exception e) {
            log.error("Error getting messages for delivery {} between users {} and {}", deliveryId, authentication.getName(), otherUserId, e);
            throw new RuntimeException("Failed to get messages: " + e.getMessage());
        }
    }

    public List<ChatRoomDTO> getUserChatRooms(Authentication authentication) {
        try {
            CustomUserDetailsService.CustomUserDetails userDetails =
                    (CustomUserDetailsService.CustomUserDetails) authentication.getPrincipal();
            String userId = userDetails.getUserId();

            log.info("Getting chat rooms for user: {}", userId);

            List<ChatRoom> chatRooms = chatRoomRepository.findUserChatRooms(userId);
            log.info("Found {} chat rooms for user: {}", chatRooms.size(), userId);

            List<ChatRoomDTO> result = chatRooms.stream()
                    .map(room -> convertToChatRoomDTO(room, userId))
                    .filter(Objects::nonNull) // Filter out any null DTOs
                    .sorted(Comparator.comparing(
                            ChatRoomDTO::getLastMessageAt,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    ))
                    .collect(Collectors.toList());

            log.info("Successfully converted {} chat rooms to DTOs", result.size());
            return result;

        } catch (Exception e) {
            log.error("Error getting user chat rooms for user: {}", authentication.getName(), e);
            throw new RuntimeException("Failed to get user chat rooms: " + e.getMessage());
        }
    }

    @Transactional
    public void markMessagesAsRead(String deliveryId, String senderId, Authentication authentication) {
        try {
            CustomUserDetailsService.CustomUserDetails userDetails =
                    (CustomUserDetailsService.CustomUserDetails) authentication.getPrincipal();
            String currentUserId = userDetails.getUserId();

            List<Message> unreadMessages = messageRepository.findMessagesBetweenUsersForDelivery(
                            senderId, currentUserId, deliveryId, Pageable.unpaged()).getContent()
                    .stream()
                    .filter(msg -> msg.getStatus() != Message.MessageStatus.READ && msg.getReceiverId().equals(currentUserId))
                    .collect(Collectors.toList());

            if (!unreadMessages.isEmpty()) {
                LocalDateTime now = LocalDateTime.now();
                unreadMessages.forEach(message -> {
                    message.setStatus(Message.MessageStatus.READ);
                    message.setReadAt(now);
                });

                messageRepository.saveAll(unreadMessages);

                // Notify sender via WebSocket
                WebSocketMessage wsMessage = new WebSocketMessage(
                        WebSocketMessage.WebSocketMessageType.MESSAGE_READ,
                        Map.of("deliveryId", deliveryId, "readCount", unreadMessages.size()),
                        senderId,
                        deliveryId
                );
                messagingTemplate.convertAndSendToUser(senderId, "/queue/messages", wsMessage);

                log.info("Marked {} messages as read for delivery {}", unreadMessages.size(), deliveryId);
            }
        } catch (Exception e) {
            log.error("Error marking messages as read for delivery {}", deliveryId, e);
        }
    }

    public long getUnreadMessageCount(String deliveryId, Authentication authentication) {
        try {
            CustomUserDetailsService.CustomUserDetails userDetails =
                    (CustomUserDetailsService.CustomUserDetails) authentication.getPrincipal();
            String userId = userDetails.getUserId();


            return messageRepository.countByDeliveryIdAndReceiverIdAndStatusNot(
                    deliveryId, userId, Message.MessageStatus.READ);

        } catch (Exception e) {
            log.error("Error getting unread message count for delivery {}", deliveryId, e);
            return 0;
        }
    }

    @Transactional
    public ChatRoom getOrCreateChatRoom(String deliveryId, String senderId, String receiverId) {
        try {
            log.info("Attempting to get or create chat room for delivery: {}", deliveryId);

            Optional<ChatRoom> existingRoom = chatRoomRepository.findByDeliveryId(deliveryId);
            if (existingRoom.isPresent()) {
                log.info("Found existing chat room: {}", existingRoom.get().getId());
                return existingRoom.get();
            }

            log.info("Creating new chat room for delivery: {}", deliveryId);

            // FIXED: Use List instead of Set for participants
            List<String> participants = new ArrayList<>();
            participants.add(senderId);
            participants.add(receiverId);

            ChatRoom newRoom = ChatRoom.builder()
                    .id(generateChatRoomId(deliveryId)) // Use local method instead of static
                    .deliveryId(deliveryId)
                    .clientId(isClient(senderId) ? senderId : receiverId)
                    .deliveryPersonId(isDeliveryPerson(senderId) ? senderId : receiverId)
                    .participants(participants) // FIXED: Use List<String>
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            ChatRoom savedRoom = chatRoomRepository.save(newRoom);
            log.info("Successfully saved chat room: {}", savedRoom.getId());

            return savedRoom;
        } catch (Exception e) {
            log.error("Error creating/getting chat room for delivery {}", deliveryId, e);
            throw new RuntimeException("Failed to create/get chat room: " + e.getMessage());
        }
    }

    private String generateChatRoomId(String deliveryId) {
        return "chat_room_" + deliveryId + "_" + System.currentTimeMillis();
    }

    private boolean isClient(String userId) {
        try {
            return userRepository.findById(userId)
                    .map(user -> user.getRoles().stream()
                            .anyMatch(role -> role.name().contains("CLIENT") ||
                                    role.name().contains("INDIVIDUAL") ||
                                    role.name().contains("ENTERPRISE")))
                    .orElse(false);
        } catch (Exception e) {
            log.error("Error checking if user {} is client", userId, e);
            return false;
        }
    }

    private boolean isDeliveryPerson(String userId) {
        try {
            return userRepository.findById(userId)
                    .map(user -> user.getRoles().stream()
                            .anyMatch(role -> role.name().contains("DELIVERY") ||
                                    role.name().contains("PROFESSIONAL") ||
                                    role.name().contains("TEMPORARY")))
                    .orElse(false);
        } catch (Exception e) {
            log.error("Error checking if user {} is delivery person", userId, e);
            return false;
        }
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
            log.debug("WebSocket message sent to user: {}", receiverId);
        } catch (Exception e) {
            log.error("Failed to send WebSocket message to user {}", receiverId, e);
        }
    }

    private MessageDTO convertToMessageDTO(Message message, User sender) {
        try {
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
        } catch (Exception e) {
            log.error("Error converting message to DTO: {}", message.getId(), e);
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

            // Safe user name handling with better error handling
            dto.setClientName(
                    Optional.ofNullable(room.getClientId())
                            .flatMap(clientId -> {
                                try {
                                    return userRepository.findById(clientId);
                                } catch (Exception e) {
                                    log.error("Error finding client by ID: {}", clientId, e);
                                    return Optional.empty();
                                }
                            })
                            .map(User::getFullName)
                            .orElse("Unknown Client")
            );

            dto.setDeliveryPersonName(
                    Optional.ofNullable(room.getDeliveryPersonId())
                            .flatMap(deliveryPersonId -> {
                                try {
                                    return userRepository.findById(deliveryPersonId);
                                } catch (Exception e) {
                                    log.error("Error finding delivery person by ID: {}", deliveryPersonId, e);
                                    return Optional.empty();
                                }
                            })
                            .map(User::getFullName)
                            .orElse("Unknown Delivery Person")
            );

            // FIXED: Count unread messages using the new method signature
            try {
                // Use the new method that returns a guaranteed long value
                long unreadCount = messageRepository.countByDeliveryIdAndReceiverIdAndStatusNot(
                        room.getDeliveryId(),
                        currentUserId,
                        Message.MessageStatus.READ
                );
                dto.setUnreadCount((int) unreadCount);
            } catch (Exception e) {
                log.error("Error counting unread messages for delivery {}", room.getDeliveryId(), e);
                dto.setUnreadCount(0);
            }

            return dto;
        } catch (Exception e) {
            log.error("Error converting ChatRoom to DTO: {}", room != null ? room.getId() : "null", e);
            return null;
        }
    }
}