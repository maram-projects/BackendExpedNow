package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.ChatRoom;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends MongoRepository<ChatRoom, String> {

    // Simple query by delivery ID
    Optional<ChatRoom> findByDeliveryId(String deliveryId);

    // Find chat room by delivery ID and participants (MongoDB syntax)
    @Query("{ 'deliveryId': ?0, $or: [ " +
            "{ 'clientId': ?1, 'deliveryPersonId': ?2 }, " +
            "{ 'clientId': ?2, 'deliveryPersonId': ?1 } ] }")
    Optional<ChatRoom> findByDeliveryIdAndParticipants(String deliveryId, String userId1, String userId2);

    // Find all user chat rooms with explicit sorting parameter
    @Query("{ $or: [ { 'clientId': ?0 }, { 'deliveryPersonId': ?0 } ] }")
    List<ChatRoom> findUserChatRoomsWithSort(String userId, Sort sort);

    // Convenience method with default sorting - FIXED: Call the correct method
    default List<ChatRoom> findUserChatRooms(String userId) {
        return findUserChatRoomsWithSort(userId, Sort.by(Sort.Direction.DESC, "lastMessageAt"));
    }

    // Find active user chat rooms with explicit sorting parameter
    @Query("{ $or: [ { 'clientId': ?0 }, { 'deliveryPersonId': ?0 } ], 'isActive': true }")
    List<ChatRoom> findActiveUserChatRoomsWithSort(String userId, Sort sort);

    // Convenience method with default sorting - FIXED: Call the correct method
    default List<ChatRoom> findActiveUserChatRooms(String userId) {
        return findActiveUserChatRoomsWithSort(userId, Sort.by(Sort.Direction.DESC, "lastMessageAt"));
    }

    // Find active chat room by delivery ID
    @Query("{ 'deliveryId': ?0, 'isActive': true }")
    Optional<ChatRoom> findActiveByDeliveryId(String deliveryId);

    // Count active user chat rooms
    @Query(value = "{ $or: [ { 'clientId': ?0 }, { 'deliveryPersonId': ?0 } ], 'isActive': true }", count = true)
    long countActiveUserChatRooms(String userId);

    // Find chat rooms by specific participants with explicit sorting parameter
    @Query("{ 'clientId': ?0, 'deliveryPersonId': ?1 }")
    List<ChatRoom> findByParticipantsWithSort(String clientId, String deliveryPersonId, Sort sort);

    // Convenience method with default sorting - FIXED: Call the correct method
    default List<ChatRoom> findByParticipants(String clientId, String deliveryPersonId) {
        return findByParticipantsWithSort(clientId, deliveryPersonId, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    // Find user chat rooms by delivery IDs
    @Query("{ 'deliveryId': { $in: ?1 }, $or: [ { 'clientId': ?0 }, { 'deliveryPersonId': ?0 } ] }")
    List<ChatRoom> findUserChatRoomsByDeliveryIds(String userId, List<String> deliveryIds);

    // Alternative: Using Spring Data method naming convention (no @Query needed)
    // These methods can replace some of the @Query methods above

    // Find by delivery ID and active status
    Optional<ChatRoom> findByDeliveryIdAndIsActiveTrue(String deliveryId);

    // Find by client ID or delivery person ID, ordered by last message date
    List<ChatRoom> findByClientIdOrDeliveryPersonIdOrderByLastMessageAtDesc(String clientId, String deliveryPersonId);

    // Find active rooms by client ID or delivery person ID
    List<ChatRoom> findByClientIdOrDeliveryPersonIdAndIsActiveTrueOrderByLastMessageAtDesc(String clientId, String deliveryPersonId);

    // Count active rooms by user
    long countByClientIdOrDeliveryPersonIdAndIsActiveTrue(String clientId, String deliveryPersonId);

    // Additional useful methods for your chat service

    // Find all chat rooms for a user (both as client and delivery person)
    default List<ChatRoom> findAllUserChatRooms(String userId) {
        return findByClientIdOrDeliveryPersonIdOrderByLastMessageAtDesc(userId, userId);
    }

    // Find only active chat rooms for a user
    default List<ChatRoom> findAllActiveUserChatRooms(String userId) {
        return findByClientIdOrDeliveryPersonIdAndIsActiveTrueOrderByLastMessageAtDesc(userId, userId);
    }

    // Count all chat rooms for a user
    default long countAllUserChatRooms(String userId) {
        return countByClientIdOrDeliveryPersonIdAndIsActiveTrue(userId, userId);
    }
}