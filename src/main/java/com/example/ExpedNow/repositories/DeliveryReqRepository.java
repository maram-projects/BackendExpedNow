package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.DeliveryRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface DeliveryReqRepository extends MongoRepository<DeliveryRequest, String> {

    List<DeliveryRequest> findByClientId(String clientId);

    List<DeliveryRequest> findByStatus(DeliveryRequest.DeliveryReqStatus status);

    List<DeliveryRequest> findByDeliveryPersonId(String deliveryPersonId);
}