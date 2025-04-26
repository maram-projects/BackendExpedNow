package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.DeliveryRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface DeliveryReqRepository extends MongoRepository<DeliveryRequest, String> {

    List<DeliveryRequest> findByClientId(String clientId);

    List<DeliveryRequest> findByStatus(DeliveryRequest.DeliveryReqStatus status);

    List<DeliveryRequest> findByDeliveryPersonId(String deliveryPersonId);

    List<DeliveryRequest> findByStatusAndDeliveryPersonId(
            DeliveryRequest.DeliveryReqStatus status,
            String deliveryPersonId
    );



    @Query("{ 'deliveryPersonId': ?0, 'status': 'PENDING' }") // تأكد من تطابق الحروف
    List<DeliveryRequest> findAssignedPendingDeliveries(String deliveryPersonId);
}