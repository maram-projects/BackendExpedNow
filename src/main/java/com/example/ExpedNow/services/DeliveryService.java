package com.example.ExpedNow.services;

import com.example.ExpedNow.models.Delivery;
import com.example.ExpedNow.repositories.DeliveryRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DeliveryService {
    private final DeliveryRepository deliveryRepository;

    public DeliveryService(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    public Delivery createDelivery(Delivery delivery) {
        return deliveryRepository.save(delivery);
    }

    public List<Delivery> getAllDeliveries() {
        return deliveryRepository.findAll();
    }

    public List<Delivery> getClientDeliveries(String clientId) {
        return deliveryRepository.findByClientId(clientId);
    }

    public List<Delivery> getPendingDeliveries() {
        return deliveryRepository.findByStatus(Delivery.DeliveryStatus.PENDING);
    }

    public Delivery updateDeliveryStatus(String id, Delivery.DeliveryStatus status) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Delivery not found"));
        delivery.setStatus(status);
        return deliveryRepository.save(delivery);
    }

    public void cancelDelivery(String id) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Delivery not found"));
        delivery.setStatus(Delivery.DeliveryStatus.CANCELLED);
        deliveryRepository.save(delivery);
    }

    public Delivery getDeliveryById(String id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Delivery not found"));
    }


}