package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.Delivery;
import com.example.ExpedNow.repositories.DeliveryRepository;
import com.example.ExpedNow.services.core.DeliveryServiceInterface;
import com.example.ExpedNow.services.core.VehicleServiceInterface;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
public class DeliveryServiceImpl implements DeliveryServiceInterface {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryServiceImpl.class);

    private final DeliveryRepository deliveryRepository;
    private final VehicleServiceInterface vehicleService;

    @Autowired
    private DeliveryAssignmentServiceImpl deliveryAssignmentService;

    public DeliveryServiceImpl(DeliveryRepository deliveryRepository, VehicleServiceInterface vehicleService) {
        this.deliveryRepository = deliveryRepository;
        this.vehicleService = vehicleService;
    }

    @Override
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

    @Override
    public List<Delivery> getAllDeliveries() {
        return deliveryRepository.findAll();
    }

    @Override
    public List<Delivery> getClientDeliveries(String clientId) {
        return deliveryRepository.findByClientId(clientId);
    }

    @Override
    public List<Delivery> getPendingDeliveries() {
        return deliveryRepository.findByStatus(Delivery.DeliveryStatus.PENDING);
    }

    @Override
    public Delivery updateDeliveryStatus(String id, Delivery.DeliveryStatus status) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));
        delivery.setStatus(status);
        return deliveryRepository.save(delivery);
    }

    @Override
    public void cancelDelivery(String id) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));
        delivery.setStatus(Delivery.DeliveryStatus.CANCELLED);
        deliveryRepository.save(delivery);
    }

    @Override
    public Delivery getDeliveryById(String id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));
    }
}