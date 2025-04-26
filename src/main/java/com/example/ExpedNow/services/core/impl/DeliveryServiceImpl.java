package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.dto.DeliveryResponseDTO;
import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.repositories.DeliveryReqRepository;
import com.example.ExpedNow.services.core.DeliveryServiceInterface;
import com.example.ExpedNow.services.core.VehicleServiceInterface;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Primary
public class DeliveryServiceImpl implements DeliveryServiceInterface {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryServiceImpl.class);

    private final DeliveryReqRepository deliveryRepository;
    private final VehicleServiceInterface vehicleService;

    @Autowired
    private DeliveryAssignmentServiceImpl deliveryAssignmentService;

    public DeliveryServiceImpl(DeliveryReqRepository deliveryRepository, VehicleServiceInterface vehicleService) {
        this.deliveryRepository = deliveryRepository;
        this.vehicleService = vehicleService;
    }

    @Override
    public DeliveryRequest createDelivery(DeliveryRequest delivery) {
        // Save the delivery first

        DeliveryRequest savedDelivery = deliveryRepository.save(delivery);

        // Only update vehicle availability if vehicleId is provided
        if (savedDelivery.getVehicleId() != null && !savedDelivery.getVehicleId().isEmpty()) {
            vehicleService.setVehicleUnavailable(savedDelivery.getVehicleId());
        }

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
    public List<DeliveryRequest> getAllDeliveries() {
        return deliveryRepository.findAll();
    }

    @Override
    public List<DeliveryRequest> getClientDeliveries(String clientId) {
        return deliveryRepository.findByClientId(clientId);
    }

    @Override
    public List<DeliveryRequest> getPendingDeliveries() {
        return deliveryRepository.findByStatus(DeliveryRequest.DeliveryReqStatus.PENDING);
    }

    @Override
    public DeliveryRequest updateDeliveryStatus(String id, DeliveryRequest.DeliveryReqStatus status) {
        DeliveryRequest delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));
        delivery.setStatus(status);
        return deliveryRepository.save(delivery);
    }

    @Override
    public void cancelDelivery(String id) {
        DeliveryRequest delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));
        delivery.setStatus(DeliveryRequest.DeliveryReqStatus.CANCELLED);
        deliveryRepository.save(delivery);
    }

    @Override
    public DeliveryRequest getDeliveryById(String id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));
    }

    @Override
    public DeliveryRequest resetDeliveryAssignment(String deliveryId, String deliveryPersonId) {
        DeliveryRequest delivery = getDeliveryById(deliveryId);

        if (!deliveryPersonId.equals(delivery.getDeliveryPersonId())) {
            throw new IllegalStateException("Not authorized to reject this delivery");
        }

        delivery.setDeliveryPersonId(null);
        delivery.setAssignedAt(null);
        return deliveryRepository.save(delivery);
    }

    @Override
    public List<DeliveryResponseDTO> getAssignedPendingDeliveries(String deliveryPersonId) {
        logger.info("Fetching assigned pending deliveries for user: {}", deliveryPersonId);

        List<DeliveryRequest> deliveries = deliveryRepository.findByStatusAndDeliveryPersonId(
                DeliveryRequest.DeliveryReqStatus.PENDING,
                deliveryPersonId
        );

        logger.info("Found {} deliveries", deliveries.size());
        return deliveries.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private DeliveryResponseDTO convertToDto(DeliveryRequest delivery) {
        return new DeliveryResponseDTO(
                delivery.getId(),
                delivery.getPickupAddress(),
                delivery.getDeliveryAddress(),
                delivery.getPackageDescription(),
                delivery.getPackageWeight(),
                delivery.getVehicleId(),
                delivery.getScheduledDate(),
                delivery.getAdditionalInstructions(),
                delivery.getStatus().name(),
                delivery.getCreatedAt(),
                delivery.getClientId()
        );
    }
}