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

    // Find by delivery ID only (active rooms)
    @Query("{ 'deliveryId': ?0, 'isActive': true }")
    Optional<ChatRoom> findByDeliveryId(String deliveryId);

    // Original method - find by delivery and participants in any order
    @Query("{ 'deliveryId': ?0, $or: [ " +
            "{ 'clientId': ?1, 'deliveryPersonId': ?2 }, " +
            "{ 'clientId': ?2, 'deliveryPersonId': ?1 } ] }")
    Optional<ChatRoom> findByDeliveryIdAndParticipants(String deliveryId, String userId1, String userId2);

    // Find all active chat rooms for a user (both as client and delivery person)
    // Sorted by lastMessageAt DESC, then createdAt DESC for null lastMessageAt
    @Query(value = "{ 'isActive': true, $or: [ { 'clientId': ?0 }, { 'deliveryPersonId': ?0 } ] }",
            sort = "{ 'lastMessageAt': -1, 'createdAt': -1 }")
    List<ChatRoom> findAllUserChatRooms(String userId);

    // Find active rooms for a user (alternative method name for clarity)
    @Query("{ 'isActive': true, $or: [ { 'clientId': ?0 }, { 'deliveryPersonId': ?0 } ] }")
    List<ChatRoom> findActiveRoomsByUserId(String userId, Sort sort);

    // Convenience method with default sorting
    default List<ChatRoom> findActiveRoomsByUserId(String userId) {
        return findActiveRoomsByUserId(userId, Sort.by(Sort.Direction.DESC, "lastMessageAt", "createdAt"));
    }

    // Find all user chat rooms (including inactive ones)
    @Query("{ $or: [ { 'clientId': ?0 }, { 'deliveryPersonId': ?0 } ] }")
    List<ChatRoom> findUserChatRoomsWithSort(String userId, Sort sort);

    // Convenience method with default sorting
    default List<ChatRoom> findUserChatRooms(String userId) {
        return findUserChatRoomsWithSort(userId, Sort.by(Sort.Direction.DESC, "lastMessageAt", "createdAt"));
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

    // Convenience method with default sorting
    default List<ChatRoom> findByParticipants(String clientId, String deliveryPersonId) {
        return findByParticipantsWithSort(clientId, deliveryPersonId, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    // Find user chat rooms by delivery IDs
    @Query("{ 'deliveryId': { $in: ?1 }, $or: [ { 'clientId': ?0 }, { 'deliveryPersonId': ?0 } ] }")
    List<ChatRoom> findUserChatRoomsByDeliveryIds(String userId, List<String> deliveryIds);

    // Using Spring Data method naming convention (alternative approaches)

    // Find by delivery ID and active status
    Optional<ChatRoom> findByDeliveryIdAndIsActiveTrue(String deliveryId);

    // Find by client ID or delivery person ID, ordered by last message date
    List<ChatRoom> findByClientIdOrDeliveryPersonIdOrderByLastMessageAtDescCreatedAtDesc(String clientId, String deliveryPersonId);

    // Find active rooms by client ID or delivery person ID
    List<ChatRoom> findByClientIdOrDeliveryPersonIdAndIsActiveTrueOrderByLastMessageAtDescCreatedAtDesc(String clientId, String deliveryPersonId);

    // Count active rooms by user
    long countByClientIdOrDeliveryPersonIdAndIsActiveTrue(String clientId, String deliveryPersonId);

    // Additional convenience methods

    // Find all chat rooms for a user using method naming
    default List<ChatRoom> findAllUserChatRoomsMethodNaming(String userId) {
        return findByClientIdOrDeliveryPersonIdOrderByLastMessageAtDescCreatedAtDesc(userId, userId);
    }

    // Find only active chat rooms for a user using method naming
    default List<ChatRoom> findAllActiveUserChatRoomsMethodNaming(String userId) {
        return findByClientIdOrDeliveryPersonIdAndIsActiveTrueOrderByLastMessageAtDescCreatedAtDesc(userId, userId);
    }

    // Count all active chat rooms for a user
    default long countAllActiveUserChatRooms(String userId) {
        return countByClientIdOrDeliveryPersonIdAndIsActiveTrue(userId, userId);
    }
}