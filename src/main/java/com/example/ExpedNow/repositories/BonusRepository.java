package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.Bonus;
import com.example.ExpedNow.models.enums.BonusStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BonusRepository extends MongoRepository<Bonus, String> {

    // ===== BASIC FINDER METHODS =====
    List<Bonus> findByDeliveryPersonId(String deliveryPersonId);

    List<Bonus> findByStatus(BonusStatus status);

    List<Bonus> findByStatusAndDeliveryPersonId(BonusStatus status, String deliveryPersonId);

    List<Bonus> findByDeliveryPersonIdAndStatus(String deliveryPersonId, BonusStatus status);

    List<Bonus> findByDeliveryPersonIdOrderByCreatedAtDesc(String deliveryPersonId);

    List<Bonus> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<Bonus> findByStatusAndDeliveryPersonIdAndCreatedAtBetween(
            BonusStatus status, String deliveryPersonId, LocalDateTime startDate, LocalDateTime endDate);

    // ===== DATE RANGE QUERIES (CONSOLIDATED) =====
    List<Bonus> findByDeliveryPersonIdAndCreatedAtBetween(
            String deliveryPersonId, LocalDateTime startDate, LocalDateTime endDate);

    // ===== NEW METHODS FOR MILESTONE BONUS TRACKING =====

    /**
     * Find bonuses by delivery person ID and reason (for duplicate milestone check)
     */
    List<Bonus> findByDeliveryPersonIdAndReason(String deliveryPersonId, String reason);

    /**
     * Find bonuses by delivery person ID and bonus type
     */
    List<Bonus> findByDeliveryPersonIdAndBonusType(String deliveryPersonId, String bonusType);

    /**
     * Count bonuses by delivery person and bonus type
     */
    long countByDeliveryPersonIdAndBonusType(String deliveryPersonId, String bonusType);

    /**
     * Find milestone bonuses ordered by creation date
     */
    @Query("{ 'deliveryPersonId': ?0, 'bonusType': 'MILESTONE' }")
    List<Bonus> findMilestoneBonusesByDeliveryPersonIdOrderByCreatedAtDesc(String deliveryPersonId);

    /**
     * Check if milestone bonus exists for specific mission count
     */
    @Query("{ 'deliveryPersonId': ?0, 'reason': { '$regex': ?1, '$options': 'i' } }")
    List<Bonus> findByDeliveryPersonIdAndReasonContaining(String deliveryPersonId, String reasonPattern);

    /**
     * Find all bonuses of specific type for analytics
     */
    List<Bonus> findByBonusType(String bonusType);

    /**
     * Count total bonuses by type in system
     */
    long countByBonusType(String bonusType);

    /**
     * Find the most recent milestone bonus for a delivery person
     */
    @Query("{ 'deliveryPersonId': ?0, 'bonusType': 'MILESTONE' }")
    Optional<Bonus> findLatestMilestoneBonus(String deliveryPersonId);

    /**
     * Check if milestone bonus exists for exact mission count
     */
    @Query("{ 'deliveryPersonId': ?0, 'bonusType': 'MILESTONE', 'deliveryCount': ?1 }")
    Optional<Bonus> findMilestoneBonusByDeliveryCount(String deliveryPersonId, int deliveryCount);

    /**
     * Find pending milestone bonuses for a delivery person
     */
    @Query("{ 'deliveryPersonId': ?0, 'bonusType': 'MILESTONE', 'status': 'CREATED' }")
    List<Bonus> findPendingMilestoneBonuses(String deliveryPersonId);

    // ===== COUNT METHODS =====
    long countByStatus(BonusStatus status);

    long countByDeliveryPersonId(String deliveryPersonId);

    long countByDeliveryPersonIdAndStatus(String deliveryPersonId, BonusStatus status);

    // ===== SUM METHODS USING AGGREGATION =====
    @Query(value = "{ 'status': ?0 }", fields = "{ 'amount': 1 }")
    List<Bonus> findBonusesByStatusForSum(BonusStatus status);

    default Double sumAmountByStatus(BonusStatus status) {
        return findBonusesByStatusForSum(status).stream()
                .mapToDouble(Bonus::getAmount)
                .sum();
    }

    @Query(value = "{}", fields = "{ 'amount': 1 }")
    List<Bonus> findAllBonusesForSum();

    default Double sumAllAmounts() {
        return findAllBonusesForSum().stream()
                .mapToDouble(Bonus::getAmount)
                .sum();
    }

    @Query("{ 'deliveryPersonId': ?0 }")
    List<Bonus> findByDeliveryPersonIdForSum(String deliveryPersonId);

    default Double sumAmountByDeliveryPersonId(String deliveryPersonId) {
        return findByDeliveryPersonIdForSum(deliveryPersonId).stream()
                .mapToDouble(Bonus::getAmount)
                .sum();
    }

    @Query("{ 'deliveryPersonId': ?0, 'status': ?1 }")
    List<Bonus> findByDeliveryPersonIdAndStatusForSum(String deliveryPersonId, BonusStatus status);

    default Double sumAmountByDeliveryPersonIdAndStatus(String deliveryPersonId, BonusStatus status) {
        return findByDeliveryPersonIdAndStatusForSum(deliveryPersonId, status).stream()
                .mapToDouble(Bonus::getAmount)
                .sum();
    }

    @Query("{ 'createdAt': { '$gte': ?0, '$lte': ?1 } }")
    List<Bonus> findByCreatedAtBetweenForSum(LocalDateTime startDate, LocalDateTime endDate);

    default Double sumAmountByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return findByCreatedAtBetweenForSum(startDate, endDate).stream()
                .mapToDouble(Bonus::getAmount)
                .sum();
    }

    /**
     * NEW: Sum amount by delivery person and bonus type
     */
    @Query("{ 'deliveryPersonId': ?0, 'bonusType': ?1 }")
    List<Bonus> findByDeliveryPersonIdAndBonusTypeForSum(String deliveryPersonId, String bonusType);

    default Double sumAmountByDeliveryPersonIdAndBonusType(String deliveryPersonId, String bonusType) {
        return findByDeliveryPersonIdAndBonusTypeForSum(deliveryPersonId, bonusType).stream()
                .mapToDouble(Bonus::getAmount)
                .sum();
    }

    /**
     * NEW: Sum amount by bonus type
     */
    @Query("{ 'bonusType': ?0 }")
    List<Bonus> findByBonusTypeForSum(String bonusType);

    default Double sumAmountByBonusType(String bonusType) {
        return findByBonusTypeForSum(bonusType).stream()
                .mapToDouble(Bonus::getAmount)
                .sum();
    }

    // ===== STATUS-SPECIFIC FINDER METHODS =====
    @Query("{ 'status': 'CREATED' }")
    List<Bonus> findCreatedBonuses();

    @Query("{ 'status': 'PAID' }")
    List<Bonus> findPaidBonuses();

    @Query("{ 'status': 'REJECTED' }")
    List<Bonus> findRejectedBonuses();

    // ===== MORE DATE RANGE QUERIES =====
    List<Bonus> findByStatusAndCreatedAtBetween(
            BonusStatus status, LocalDateTime startDate, LocalDateTime endDate);

    // ===== ACTIVE BONUSES (CREATED AND PAID) =====
    @Query("{ 'deliveryPersonId': ?0, 'status': { '$in': ['CREATED', 'PAID'] } }")
    List<Bonus> findActiveByDeliveryPersonId(String deliveryPersonId);

    @Query("{ 'status': { '$in': ['CREATED', 'PAID'] } }")
    List<Bonus> findAllActive();

    // ===== CUSTOM COUNT QUERIES FOR STATISTICS =====
    @Query(value = "{ 'status': 'CREATED' }", count = true)
    long countCreatedBonuses();

    @Query(value = "{ 'status': 'PAID' }", count = true)
    long countPaidBonuses();

    @Query(value = "{ 'status': 'REJECTED' }", count = true)
    long countRejectedBonuses();

    // ===== MONTHLY/PERIOD STATISTICS =====
    @Query("{ 'createdAt': { '$gte': ?0, '$lt': ?1 }, 'status': 'PAID' }")
    List<Bonus> findPaidBonusesInPeriod(LocalDateTime startDate, LocalDateTime endDate);

    // ===== FIND BONUSES BY MULTIPLE CRITERIA =====
    @Query("{ 'deliveryPersonId': ?0, 'status': ?1, 'createdAt': { '$gte': ?2, '$lte': ?3 } }")
    List<Bonus> findByDeliveryPersonIdAndStatusAndCreatedAtBetween(
            String deliveryPersonId, BonusStatus status, LocalDateTime startDate, LocalDateTime endDate);

    // ===== ADMIN TRACKING METHODS =====
    List<Bonus> findByCreatedBy(String createdBy);

    List<Bonus> findByPaidBy(String paidBy);

    List<Bonus> findByRejectedBy(String rejectedBy);

    // ===== ACTIVE/INACTIVE STATUS =====
    List<Bonus> findByIsActiveTrue();

    List<Bonus> findByIsActiveFalse();

    // ===== COMPLEX QUERIES FOR REPORTING =====
    @Query("{ 'paidAt': { '$gte': ?0, '$lte': ?1 } }")
    List<Bonus> findBonusesPaidBetween(LocalDateTime startDate, LocalDateTime endDate);

    @Query("{ 'rejectedAt': { '$gte': ?0, '$lte': ?1 } }")
    List<Bonus> findBonusesRejectedBetween(LocalDateTime startDate, LocalDateTime endDate);

    // ===== FIND BONUSES WITH AMOUNTS IN RANGE =====
    List<Bonus> findByAmountBetween(double minAmount, double maxAmount);

    // ===== FIND BONUSES BY DELIVERY COUNT THRESHOLD =====
    List<Bonus> findByDeliveryCountGreaterThanEqual(int minDeliveryCount);

    // ===== ADVANCED MILESTONE BONUS METHODS =====

    /**
     * Find all milestone bonuses for a delivery person with specific status
     */
    @Query("{ 'deliveryPersonId': ?0, 'bonusType': 'MILESTONE', 'status': ?1 }")
    List<Bonus> findMilestoneBonusesByDeliveryPersonIdAndStatus(String deliveryPersonId, BonusStatus status);

    /**
     * Count milestone bonuses for a delivery person with specific status
     */
    @Query(value = "{ 'deliveryPersonId': ?0, 'bonusType': 'MILESTONE', 'status': ?1 }", count = true)
    long countMilestoneBonusesByDeliveryPersonIdAndStatus(String deliveryPersonId, BonusStatus status);

    /**
     * Find milestone bonuses created within a specific period
     */
    @Query("{ 'bonusType': 'MILESTONE', 'createdAt': { '$gte': ?0, '$lte': ?1 } }")
    List<Bonus> findMilestoneBonusesInPeriod(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Get the highest delivery count milestone reached by a delivery person
     */
    @Query(value = "{ 'deliveryPersonId': ?0, 'bonusType': 'MILESTONE' }", sort = "{ 'deliveryCount': -1 }")
    Optional<Bonus> findHighestMilestoneReached(String deliveryPersonId);

    /**
     * Find all unique milestone levels achieved in the system
     */
    @Aggregation(pipeline = {
            "{ '$match': { 'bonusType': 'MILESTONE' } }",
            "{ '$group': { '_id': '$deliveryCount', 'count': { '$sum': 1 }, 'totalAmount': { '$sum': '$amount' } } }",
            "{ '$sort': { '_id': 1 } }"
    })
    List<Object> getMilestoneStatistics();

    /**
     * Find delivery persons who have reached a specific milestone
     */
    @Query("{ 'bonusType': 'MILESTONE', 'deliveryCount': ?0 }")
    List<Bonus> findDeliveryPersonsAtMilestone(int milestoneCount);

    /**
     * Count active milestone bonuses awaiting payment
     */
    @Query(value = "{ 'bonusType': 'MILESTONE', 'status': 'CREATED' }", count = true)
    long countPendingMilestoneBonuses();

    /**
     * Find recently created milestone bonuses (last 7 days)
     */
    @Query("{ 'bonusType': 'MILESTONE', 'createdAt': { '$gte': ?0 } }")
    List<Bonus> findRecentMilestoneBonuses(LocalDateTime sevenDaysAgo);

    // ===== PAGINATION SUPPORT METHODS =====

    /**
     * Find bonuses by delivery person with limit for pagination
     */
    @Query("{ 'deliveryPersonId': ?0 }")
    List<Bonus> findByDeliveryPersonIdWithLimit(String deliveryPersonId);

    /**
     * Find recent bonuses with limit
     */
    @Query(value = "{ 'deliveryPersonId': ?0 }", sort = "{ 'createdAt': -1 }")
    List<Bonus> findRecentBonusesByDeliveryPersonId(String deliveryPersonId);

    /**
     * Find top N bonuses by amount for a delivery person
     */
    @Query(value = "{ 'deliveryPersonId': ?0 }", sort = "{ 'amount': -1 }")
    List<Bonus> findTopBonusesByAmount(String deliveryPersonId);
}