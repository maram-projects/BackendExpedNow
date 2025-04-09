package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.Delivery;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface DeliveryRepository extends MongoRepository<Delivery, String> {

    List<Delivery> findByClientId(String clientId);

    List<Delivery> findByStatus(Delivery.DeliveryStatus status);

    List<Delivery> findByDeliveryPersonId(String deliveryPersonId);
}