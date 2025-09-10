package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    @Query("{'$or': [" +
            "{'senderId': ?0, 'receiverId': ?1}, " +
            "{'senderId': ?1, 'receiverId': ?0}" +
            "], 'deliveryId': ?2}")
    Page<Message> findMessagesBetweenUsersForDelivery(
            String userId1, String userId2, String deliveryId, Pageable pageable);

    @Query("{'receiverId': ?0, 'status': {'$ne': 'read'}}")
    List<Message> findUnreadMessagesByReceiver(String receiverId);

    // MISSING METHOD ADDED: Find unread messages between specific users for a delivery
    @Query("{ 'senderId': ?0, 'receiverId': ?1, 'deliveryId': ?2, 'status': { $ne: 'READ' } }")
    List<Message> findUnreadMessagesBetweenUsers(String senderId, String receiverId, String deliveryId);

    // Count unread messages by delivery and receiver
    long countByDeliveryIdAndReceiverIdAndStatusNot(String deliveryId, String receiverId, Message.MessageStatus status);

    @Query("{'deliveryId': ?0}")
    List<Message> findMessagesByDeliveryId(String deliveryId);

    @Query("{'$or': [{'senderId': ?0}, {'receiverId': ?0}], 'timestamp': {'$gte': ?1}}")
    List<Message> findRecentMessagesByUser(String userId, LocalDateTime since);

    // Additional useful methods for better performance and functionality

    // Find all messages for a specific delivery, ordered by timestamp
    @Query("{'deliveryId': ?0}")
    Page<Message> findByDeliveryIdOrderByTimestampDesc(String deliveryId, Pageable pageable);

    // Count total messages between two users for a delivery
    @Query(value = "{'$or': [" +
            "{'senderId': ?0, 'receiverId': ?1}, " +
            "{'senderId': ?1, 'receiverId': ?0}" +
            "], 'deliveryId': ?2}", count = true)
    long countMessagesBetweenUsersForDelivery(String userId1, String userId2, String deliveryId);

    // Find messages by status
    List<Message> findByStatusOrderByTimestampDesc(Message.MessageStatus status);

    // Find messages by sender and delivery
    List<Message> findBySenderIdAndDeliveryIdOrderByTimestampDesc(String senderId, String deliveryId);

    // Find messages by receiver and delivery
    List<Message> findByReceiverIdAndDeliveryIdOrderByTimestampDesc(String receiverId, String deliveryId);

    // Count unread messages for a specific user across all deliveries
    long countByReceiverIdAndStatusNot(String receiverId, Message.MessageStatus status);

    // Find messages with attachments
    @Query("{'attachmentUrl': {'$ne': null, '$ne': ''}}")
    List<Message> findMessagesWithAttachments();

    // Find messages by type
    List<Message> findByTypeOrderByTimestampDesc(Message.MessageType type);
}