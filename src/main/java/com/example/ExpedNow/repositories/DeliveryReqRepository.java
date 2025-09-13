package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.DeliveryRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryReqRepository extends MongoRepository<DeliveryRequest, String> {

    List<DeliveryRequest> findByClientId(String clientId);
    List<DeliveryRequest> findByStatus(DeliveryRequest.DeliveryReqStatus status);
    List<DeliveryRequest> findByDeliveryPersonId(String deliveryPersonId);
    List<DeliveryRequest> findByStatusAndDeliveryPersonId(
            DeliveryRequest.DeliveryReqStatus status,
            String deliveryPersonId
    );

    // Add this method for the BonusService
    int countByDeliveryPersonIdAndStatusAndCompletedAtBetween(
            String deliveryPersonId,
            DeliveryRequest.DeliveryReqStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    @Query("{ 'deliveryPersonId': ?0, 'status': 'ASSIGNED' }")
    List<DeliveryRequest> findAssignedPendingDeliveries(String deliveryPersonId);

    List<DeliveryRequest> findByStatusAndDeliveryPersonIdIsNull(DeliveryRequest.DeliveryReqStatus status);

    @Query("{ 'deliveryPersonId': ?0, 'status': { $in: ['ASSIGNED', 'APPROVED', 'IN_TRANSIT'] } }")
    List<DeliveryRequest> findActiveDeliveriesByDeliveryPerson(String deliveryPersonId);

    @Query("{ 'vehicleId': ?0 }")
    List<DeliveryRequest> findByVehicleId(String vehicleId);

    List<DeliveryRequest> findByStatusInAndDeliveryPersonId(List<DeliveryRequest.DeliveryReqStatus> statuses, String deliveryPersonId);

    @Query("{ 'deliveryPersonId': ?0, 'status': { $in: ['APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED'] } }")
    List<DeliveryRequest> findDeliveryHistoryByDeliveryPerson(String deliveryPersonId, Sort sort);

    List<DeliveryRequest> findByStatusAndCreatedAtBefore(
            DeliveryRequest.DeliveryReqStatus status,
            LocalDateTime date
    );

    // الطريقة الصحيحة لتحديث حالة الطلبات في MongoDB
    @Query("{ 'status': 'PENDING', 'createdAt': { $lt: ?0 } }")
    @Update("{ $set: { 'status': ?1 } }")
    void expirePendingDeliveriesOlderThan(LocalDateTime cutoffDate, DeliveryRequest.DeliveryReqStatus newStatus);

    // طريقة بديلة إذا كنت تريد معرفة عدد المستندات المحدثة
    @Query(value = "{ 'status': 'PENDING', 'createdAt': { $lt: ?0 } }", count = true)
    long countPendingDeliveriesOlderThan(LocalDateTime cutoffDate);

    Optional<DeliveryRequest> findById(String id);

    // *** الطريقة الجديدة اللي نحتاجوها للـ Recent Activity ***
    @Query("{ 'deliveryPersonId': ?0, 'status': 'COMPLETED' }")
    List<DeliveryRequest> findByDeliveryPersonIdAndStatusOrderByCompletedAtDesc(String deliveryPersonId);

    // أو يمكن نستعمل هادي اللي أوضح:
    @Query(value = "{ 'deliveryPersonId': ?0, 'status': 'COMPLETED' }",
            sort = "{ 'completedAt': -1 }")
    List<DeliveryRequest> findLastCompletedDeliveryByUser(String userId);

    // Add these methods for rating functionality
    @Query("{ 'deliveryPersonId': ?0, 'status': ?1, 'createdAt': { $gt: ?2 } }")
    List<DeliveryRequest> findByDeliveryPersonIdAndStatusAndRatedAtAfter(
            String deliveryPersonId,
            DeliveryRequest.DeliveryReqStatus status,
            LocalDateTime ratedAt
    );

    @Query("{ 'status': ?0, 'createdAt': { $gt: ?1 } }")
    List<DeliveryRequest> findByStatusAndRatedAtAfter(
            DeliveryRequest.DeliveryReqStatus status,
            LocalDateTime ratedAt
    );

    @Query(value = "{ 'clientId': ?0, 'rated': ?1 }", sort = "{ 'createdAt': -1 }")
    List<DeliveryRequest> findByClientIdAndRatedOrderByCreatedAtDesc(String clientId, boolean rated);

    List<DeliveryRequest> findByDeliveryPersonIdAndRated(String deliveryPersonId, boolean rated);
    List<DeliveryRequest> findByRated(boolean rated);

    /**
     * Find deliveries by delivery person and status
     */
    List<DeliveryRequest> findByDeliveryPersonIdAndStatus(
            String deliveryPersonId,
            DeliveryRequest.DeliveryReqStatus status
    );

    /**
     * Find deliveries by delivery person and rated status, ordered by completion date
     */
    List<DeliveryRequest> findByDeliveryPersonIdAndRatedOrderByCompletedAtDesc(
            String deliveryPersonId,
            boolean rated
    );

    /**
     * Count total deliveries by delivery person
     */
    @Query(value = "{ 'deliveryPersonId': ?0 }", count = true)
    long countByDeliveryPersonId(String deliveryPersonId);

    /**
     * Find recent deliveries for performance analysis
     */
    @Query("{ 'deliveryPersonId': ?0, 'completedAt': { $gte: ?1 } }")
    List<DeliveryRequest> findRecentDeliveriesByDeliveryPerson(
            String deliveryPersonId,
            LocalDateTime since
    );

    /**
     * Get delivery statistics for a specific delivery person
     */
    @Aggregation(pipeline = {
            "{ $match: { 'deliveryPersonId': ?0, 'rated': true } }",
            "{ $group: { " +
                    "'_id': '$deliveryPersonId', " +
                    "'totalRatings': { $sum: 1 }, " +
                    "'averageRating': { $avg: '$rating' }, " +
                    "'minRating': { $min: '$rating' }, " +
                    "'maxRating': { $max: '$rating' }, " +
                    "'ratingsAbove4': { $sum: { $cond: [{ $gte: ['$rating', 4] }, 1, 0] } }, " +
                    "'ratingsBelow3': { $sum: { $cond: [{ $lt: ['$rating', 3] }, 1, 0] } } " +
                    "} }"
    })
    AggregationResult getDeliveryPersonRatingStats(String deliveryPersonId);

    // Interface for aggregation result
    interface AggregationResult {
        String getId();
        int getTotalRatings();
        double getAverageRating();
        double getMinRating();
        double getMaxRating();
        int getRatingsAbove4();
        int getRatingsBelow3();
    }


    // Add this method to your DeliveryReqRepository interface


}