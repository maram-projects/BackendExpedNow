package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.Payment;
import com.example.ExpedNow.models.enums.PaymentMethod;
import com.example.ExpedNow.models.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, String> {

    // Basic finder methods
    List<Payment> findByDeliveryId(String deliveryId);
    List<Payment> findByStatus(PaymentStatus status);
    List<Payment> findByMethod(PaymentMethod method); // Note: field name is 'method' in your model

    // Transaction ID methods for Stripe integration

    // Combined finder methods
    Optional<Payment> findByIdAndStatus(String id, PaymentStatus status);
    List<Payment> findByClientIdAndStatus(String clientId, PaymentStatus status);
    List<Payment> findByDeliveryIdAndStatus(String deliveryId, PaymentStatus status);

    // Count methods for statistics
    long countByStatus(PaymentStatus status);
    long countByMethod(PaymentMethod method); // Note: field name is 'method' in your model

    // Revenue calculation method using MongoDB aggregation
    @Aggregation(pipeline = {
            "{ '$match': { 'status': 'COMPLETED' } }",
            "{ '$group': { '_id': null, 'total': { '$sum': '$finalAmountAfterDiscount' } } }"
    })
    Optional<Double> sumCompletedPayments();

    // Alternative revenue calculation using amount field
    @Aggregation(pipeline = {
            "{ '$match': { 'status': 'COMPLETED' } }",
            "{ '$group': { '_id': null, 'total': { '$sum': '$amount' } } }"
    })
    Optional<Double> sumCompletedPaymentsByAmount();

    // Additional useful query methods
    @Query("{ 'status': ?0, 'method': ?1 }")
    List<Payment> findByStatusAndMethod(PaymentStatus status, PaymentMethod method);

    // Find payments within a date range
    @Query("{ 'createdAt': { '$gte': ?0, '$lte': ?1 } }")
    List<Payment> findPaymentsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    // Find payments with discount applied
    @Query("{ 'discountCode': { '$ne': null, '$ne': '' } }")
    List<Payment> findPaymentsWithDiscount();

    // Find payments by discount code
    List<Payment> findByDiscountCode(String discountCode);

    // Find pending payments older than specified date
    @Query("{ 'status': 'PENDING', 'createdAt': { '$lt': ?0 } }")
    List<Payment> findPendingPaymentsOlderThan(LocalDateTime date);

    // Find payments by client with pagination
    Page<Payment> findByClientId(String clientId, Pageable pageable);

    // Find payments by delivery with pagination
    Page<Payment> findByDeliveryId(String deliveryId, Pageable pageable);

    // Find payments by status with pagination
    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    // Find payments by method with pagination
    Page<Payment> findByMethod(PaymentMethod method, Pageable pageable);

    // Complex query for filtering (used by getAllPayments in service)
    @Query("{ $and: [ " +
            "{ $or: [ { 'status': { $exists: false } }, { 'status': ?0 } ] }, " +
            "{ $or: [ { 'method': { $exists: false } }, { 'method': ?1 } ] }, " +
            "{ $or: [ { 'clientId': { $exists: false } }, { 'clientId': ?2 } ] }, " +
            "{ $or: [ { 'deliveryId': { $exists: false } }, { 'deliveryId': ?3 } ] } " +
            "] }")
    Page<Payment> findWithFilters(PaymentStatus status, PaymentMethod method,
                                  String clientId, String deliveryId, Pageable pageable);

    @Query("{ 'metadata.transactionId': ?0 }")
    Optional<Payment> findByMetadataTransactionId(String transactionId);

    Optional<Payment> findByTransactionId(String transactionId);
    @Query("{ 'transactionId': ?0 }")
    Optional<Payment> findByStripePaymentIntentId(String paymentIntentId);

    List<Payment> findByClientId(String clientId);

    List<Payment> findByDeliveryPersonId(String deliveryPersonId);

    @Query("{ 'deliveryPersonPaid': false, 'status': 'COMPLETED' }")
    List<Payment> findUnpaidDeliveryPayments();
    List<Payment> findByDeliveryPersonIdAndDeliveryPersonPaidTrue(String deliveryPersonId);
}