package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, String> {
    List<Payment> findByClientId(String clientId);
    List<Payment> findByDeliveryId(String deliveryId);
    List<Payment> findByStatus(String status);
}
