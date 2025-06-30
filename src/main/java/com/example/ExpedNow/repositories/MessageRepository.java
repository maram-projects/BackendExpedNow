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

    // SOLUTION 2: Use Spring Data method naming convention (more reliable)
    long countByDeliveryIdAndReceiverIdAndStatusNot(String deliveryId, String receiverId, Message.MessageStatus status);

    @Query("{'deliveryId': ?0}")
    List<Message> findMessagesByDeliveryId(String deliveryId);

    @Query("{'$or': [{'senderId': ?0}, {'receiverId': ?0}], 'timestamp': {'$gte': ?1}}")
    List<Message> findRecentMessagesByUser(String userId, LocalDateTime since);
}