package com.example.ExpedNow.services;

import com.example.ExpedNow.models.Delivery;
import com.example.ExpedNow.repositories.DeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DeliveryService {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryService.class);

    private final DeliveryRepository deliveryRepository;
    private final VehicleService vehicleService;

    @Autowired
    private DeliveryAssignmentService deliveryAssignmentService;

    public DeliveryService(DeliveryRepository deliveryRepository, VehicleService vehicleService) {
        this.deliveryRepository = deliveryRepository;
        this.vehicleService = vehicleService;
    }

    public Delivery createDelivery(Delivery delivery) {
        // Save the delivery first
        Delivery savedDelivery = deliveryRepository.save(delivery);
        // Update vehicle availability
        vehicleService.setVehicleUnavailable(savedDelivery.getVehicleId());

        // Try to assign the delivery immediately if possible
        try {
            deliveryAssignmentService.assignDelivery(savedDelivery.getId());
            logger.info("Delivery {} automatically assigned upon creation", savedDelivery.getId());
        } catch (Exception e) {
            // If immediate assignment fails (e.g., no available delivery persons),
            // the scheduled task will handle it later
            logger.info("Immediate assignment failed for delivery {}. Will be picked up by scheduled assignment.",
                    savedDelivery.getId());
        }

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