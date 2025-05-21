package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.DeliveryRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

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
}