package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.ChatRoom;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends MongoRepository<ChatRoom, String> {

    Optional<ChatRoom> findByDeliveryId(String deliveryId);

    @Query("{'participants': ?0, 'isActive': true}")
    List<ChatRoom> findActiveRoomsByUserId(String userId);

    @Query("{'$or': [{'clientId': ?0}, {'deliveryPersonId': ?0}], 'isActive': true}")
    List<ChatRoom> findUserChatRooms(String userId);

    @Query("{'clientId': ?0, 'deliveryPersonId': ?1, 'deliveryId': ?2}")
    Optional<ChatRoom> findByParticipantsAndDelivery(String clientId, String deliveryPersonId, String deliveryId);
}