package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.DeliveryRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
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



}