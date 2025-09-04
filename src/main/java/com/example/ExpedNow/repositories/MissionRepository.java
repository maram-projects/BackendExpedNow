package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.Mission;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MissionRepository extends MongoRepository<Mission, String> {

    // ===== EXISTING BASIC QUERIES =====

    /**
     * Find missions by delivery person ID
     */
    @Query("{ 'deliveryPerson.$id': ObjectId(?0) }")
    List<Mission> findByDeliveryPersonId(String deliveryPersonId);

    /**
     * Find missions by status (multiple statuses)
     */
    @Query("{ 'status': { '$in': ?0 } }")
    List<Mission> findByStatusIn(List<String> statuses);

    /**
     * Find mission by delivery request ID
     */
    @Query("{ 'deliveryRequest.$id': ObjectId(?0) }")
    Optional<Mission> findByDeliveryRequestId(String deliveryRequestId);

    // ===== ACTIVE MISSIONS QUERIES =====

    /**
     * Find active missions for a delivery person (PENDING or IN_PROGRESS)
     */
    @Query("{ 'deliveryPerson.$id': ObjectId(?0), 'status': { '$in': ['PENDING', 'IN_PROGRESS'] } }")
    List<Mission> findActiveMissionsByDeliveryPersonId(String deliveryPersonId);

    /**
     * Find missions by delivery person and specific statuses
     */
    @Query("{ 'deliveryPerson.$id': ObjectId(?0), 'status': { '$in': ?1 } }")
    List<Mission> findByDeliveryPersonIdAndStatusIn(String deliveryPersonId, List<String> statuses);

    /**
     * Check if delivery person has any active missions (returns count > 0)
     */
    @Query(value = "{ 'deliveryPerson.$id': ObjectId(?0), 'status': { '$in': ['PENDING', 'IN_PROGRESS'] } }", count = true)
    long countActiveMissionsByDeliveryPersonId(String deliveryPersonId);

    /**
     * Check if delivery person has any missions with specific statuses
     */
    @Query(value = "{ 'deliveryPerson.$id': ObjectId(?0), 'status': { '$in': ?1 } }", count = true)
    long countByDeliveryPersonIdAndStatusIn(String deliveryPersonId, List<String> statuses);

    /**
     * Check if delivery person has any active missions (boolean result)
     */
    default boolean hasActiveMissions(String deliveryPersonId) {
        return countActiveMissionsByDeliveryPersonId(deliveryPersonId) > 0;
    }

    /**
     * Check if delivery person has missions with specific statuses (boolean result)
     */
    default boolean existsByDeliveryPersonIdAndStatusIn(String deliveryPersonId, List<String> statuses) {
        return countByDeliveryPersonIdAndStatusIn(deliveryPersonId, statuses) > 0;
    }

    // ===== COMPLETED MISSIONS QUERIES =====

    /**
     * Find completed missions for a delivery person
     */
    @Query("{ 'deliveryPerson.$id': ObjectId(?0), 'status': 'COMPLETED' }")
    List<Mission> findCompletedMissionsByDeliveryPersonId(String deliveryPersonId);

    /**
     * Count completed missions for a specific delivery person
     */
    @Query(value = "{ 'deliveryPerson.$id': ObjectId(?0), 'status': 'COMPLETED' }", count = true)
    long countCompletedMissionsByDeliveryPersonId(String deliveryPersonId);

    /**
     * Count missions by delivery person ID and specific status
     */
    @Query(value = "{ 'deliveryPerson.$id': ObjectId(?0), 'status': ?1 }", count = true)
    long countByDeliveryPersonIdAndStatus(String deliveryPersonId, String status);

    /**
     * Find completed missions ordered by end time (most recent first)
     */
    @Query(value = "{ 'deliveryPerson.$id': ObjectId(?0), 'status': 'COMPLETED' }", sort = "{ 'endTime': -1 }")
    List<Mission> findCompletedMissionsByDeliveryPersonIdOrderByEndTimeDesc(String deliveryPersonId);

    // ===== VALIDATION QUERIES =====

    /**
     * Find mission by delivery request ID and delivery person ID (for validation)
     */
    @Query("{ 'deliveryRequest.$id': ObjectId(?0), 'deliveryPerson.$id': ObjectId(?1) }")
    Optional<Mission> findByDeliveryRequestIdAndDeliveryPersonId(String deliveryRequestId, String deliveryPersonId);

    // ===== AGGREGATION QUERIES =====

    /**
     * Get missions with populated delivery request data using aggregation
     */
    @Aggregation(pipeline = {
            "{ '$match': { 'deliveryPerson.$id': ObjectId(?0) } }",
            "{ '$lookup': { 'from': 'deliveriesRequest', 'localField': 'deliveryRequest.$id', 'foreignField': '_id', 'as': 'deliveryRequestData' } }",
            "{ '$unwind': '$deliveryRequestData' }"
    })
    List<Mission> findMissionsWithDeliveryRequestByDeliveryPersonId(String deliveryPersonId);

    /**
     * Get missions with delivery request data filtered by status
     */
    @Aggregation(pipeline = {
            "{ '$match': { 'deliveryPerson.$id': ObjectId(?0), 'status': { '$in': ?1 } } }",
            "{ '$lookup': { 'from': 'deliveriesRequest', 'localField': 'deliveryRequest.$id', 'foreignField': '_id', 'as': 'deliveryRequestData' } }",
            "{ '$unwind': '$deliveryRequestData' }"
    })
    List<Mission> findMissionsWithDeliveryRequestByDeliveryPersonIdAndStatusIn(String deliveryPersonId, List<String> statuses);

    // ===== STATISTICS QUERIES =====

    /**
     * Count all missions for a delivery person
     */
    @Query(value = "{ 'deliveryPerson.$id': ObjectId(?0) }", count = true)
    long countAllMissionsByDeliveryPersonId(String deliveryPersonId);

    /**
     * Get mission statistics using aggregation
     */
    @Aggregation(pipeline = {
            "{ '$match': { 'deliveryPerson.$id': ObjectId(?0) } }",
            "{ '$group': { '_id': '$status', 'count': { '$sum': 1 } } }"
    })
    List<Object> getMissionStatisticsByDeliveryPersonId(String deliveryPersonId);

    // ===== RECENT MISSIONS QUERIES =====

    /**
     * Find recent missions (last N missions) for a delivery person
     */
    @Query(value = "{ 'deliveryPerson.$id': ObjectId(?0) }", sort = "{ 'createdAt': -1 }")
    List<Mission> findRecentMissionsByDeliveryPersonId(String deliveryPersonId);

    /**
     * Find recent completed missions for bonus validation
     */
    @Query(value = "{ 'deliveryPerson.$id': ObjectId(?0), 'status': 'COMPLETED' }", sort = "{ 'endTime': -1 }")
    List<Mission> findRecentCompletedMissionsByDeliveryPersonId(String deliveryPersonId);


    /**
     * Count missions by delivery person ID (alias for consistency)
     */
    @Query(value = "{ 'deliveryPerson.$id': ObjectId(?0) }", count = true)
    long countByDeliveryPersonId(String deliveryPersonId);
}