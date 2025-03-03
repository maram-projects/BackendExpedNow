package com.example.ExpedNow.services;

import com.example.ExpedNow.models.Delivery;
import com.example.ExpedNow.repositories.DeliveryRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DeliveryService {
    private final DeliveryRepository deliveryRepository;
    private final VehicleService vehicleService; // Add this

    // Update constructor to include VehicleService
    public DeliveryService(DeliveryRepository deliveryRepository, VehicleService vehicleService) {
        this.deliveryRepository = deliveryRepository;
        this.vehicleService = vehicleService; // Initialize
    }

    public Delivery createDelivery(Delivery delivery) {
        // Save the delivery first
        Delivery savedDelivery = deliveryRepository.save(delivery);
        // Update vehicle availability
        vehicleService.setVehicleUnavailable(savedDelivery.getVehicleId()); // Add this
        return savedDelivery;
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