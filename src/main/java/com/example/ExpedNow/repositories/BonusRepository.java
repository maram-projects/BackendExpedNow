package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.Bonus;
import com.example.ExpedNow.models.enums.BonusStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BonusRepository extends MongoRepository<Bonus, String> {

    // Basic queries
    List<Bonus> findByDeliveryPersonId(String deliveryPersonId);
    List<Bonus> findByStatus(BonusStatus status);
    List<Bonus> findByDeliveryPersonIdAndStatus(String deliveryPersonId, BonusStatus status);

    // Count queries
    long countByDeliveryPersonId(String deliveryPersonId);
    long countByStatus(BonusStatus status);
    long countByDeliveryPersonIdAndStatus(String deliveryPersonId, BonusStatus status);

    // Date range queries
    List<Bonus> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<Bonus> findByStatusAndDeliveryPersonIdAndCreatedAtBetween(
            BonusStatus status, String deliveryPersonId, LocalDateTime start, LocalDateTime end);

    // Ordered queries
    List<Bonus> findByDeliveryPersonIdOrderByCreatedAtDesc(String deliveryPersonId);
    List<Bonus> findByStatusAndDeliveryPersonId(BonusStatus status, String deliveryPersonId);

    // Aggregation queries
    @Query(value = "{}", fields = "{ 'amount': 1 }")
    List<Bonus> findAllWithAmountOnly();

    @Query("{ 'createdAt': { $gte: ?0, $lte: ?1 } }")
    List<Bonus> findByCreatedAtBetweenCustom(LocalDateTime start, LocalDateTime end);

    // Sum queries using aggregation
    @Query(value = "{}", count = true)
    long countAll();

    // Default methods for sum calculations
    default Double sumAllAmounts() {
        return findAll().stream()
                .mapToDouble(Bonus::getAmount)
                .sum();
    }

    default Double sumAmountByCreatedAtBetween(LocalDateTime start, LocalDateTime end) {
        return findByCreatedAtBetween(start, end).stream()
                .mapToDouble(Bonus::getAmount)
                .sum();
    }

    default Double sumAmountByDeliveryPersonId(String deliveryPersonId) {
        return findByDeliveryPersonId(deliveryPersonId).stream()
                .mapToDouble(Bonus::getAmount)
                .sum();
    }

    // ADD THIS MISSING METHOD
    default Double sumAmountByDeliveryPersonIdAndStatus(String deliveryPersonId, BonusStatus status) {
        return findByDeliveryPersonIdAndStatus(deliveryPersonId, status).stream()
                .mapToDouble(Bonus::getAmount)
                .sum();
    }
}