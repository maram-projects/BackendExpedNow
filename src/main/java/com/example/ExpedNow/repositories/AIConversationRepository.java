package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.AIConversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AIConversationRepository extends MongoRepository<AIConversation, String> {

    // Find conversations by user ID
    List<AIConversation> findByUserIdOrderByLastActivityDesc(String userId);

    // Find active conversations by user ID
    List<AIConversation> findByUserIdAndActiveOrderByLastActivityDesc(String userId, boolean active);

    // Find the most recent active conversation for a user
    Optional<AIConversation> findFirstByUserIdAndActiveOrderByLastActivityDesc(String userId, boolean active);

    // Find conversations created within a date range
    @Query("{'userId': ?0, 'startTime': {'$gte': ?1, '$lte': ?2}}")
    List<AIConversation> findByUserIdAndStartTimeBetween(String userId, LocalDateTime startDate, LocalDateTime endDate);

    // Find conversations with last activity before a certain date (for cleanup)
    @Query("{'lastActivity': {'$lt': ?0}}")
    List<AIConversation> findByLastActivityBefore(LocalDateTime cutoffDate);

    // Count conversations by user
    long countByUserId(String userId);

    // Count active conversations by user
    long countByUserIdAndActive(String userId, boolean active);

    // Delete old inactive conversations (for maintenance)
    void deleteByLastActivityBeforeAndActive(LocalDateTime cutoffDate, boolean active);
}