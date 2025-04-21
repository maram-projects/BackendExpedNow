package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.dto.LocationDTO;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.repositories.DeliveryReqRepository;
import com.example.ExpedNow.repositories.UserRepository;
import com.example.ExpedNow.services.core.DeliveryAssignmentServiceInterface;
import com.example.ExpedNow.services.core.LocationServiceInterface;
import com.example.ExpedNow.services.core.NotificationServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Primary
public class DeliveryAssignmentServiceImpl implements DeliveryAssignmentServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryAssignmentServiceImpl.class);

    private final DeliveryReqRepository deliveryRepository;
    private final UserRepository userRepository;
    private final NotificationServiceInterface notificationService;
    private final LocationServiceInterface locationService;

    public DeliveryAssignmentServiceImpl(
            DeliveryReqRepository deliveryRepository,
            UserRepository userRepository,
            NotificationServiceInterface notificationService,
            LocationServiceInterface locationService) {
        this.deliveryRepository = deliveryRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.locationService = locationService;
    }

    /**
     * Assigns a delivery to the closest available delivery person
     * @param deliveryId the delivery to assign
     * @return the updated delivery with assignment information
     */
    @Override
    public DeliveryRequest assignDelivery(String deliveryId) {
        DeliveryRequest delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + deliveryId));

        if (delivery.getStatus() != DeliveryRequest.DeliveryReqStatus.PENDING) {
            throw new IllegalStateException("Only pending deliveries can be assigned");
        }

        // Get available delivery persons
        List<User> availablePersons = findAvailableDeliveryPersons();

        if (availablePersons.isEmpty()) {
            logger.warn("No available delivery persons found");
            return delivery;
        }

        // Get pickup location from delivery
        LocationDTO pickupLocation = new LocationDTO();
        pickupLocation.setLatitude(delivery.getPickupLatitude());
        pickupLocation.setLongitude(delivery.getPickupLongitude());

        // Find closest delivery person
        User closestPerson = findClosestDeliveryPerson(availablePersons, pickupLocation);
        String deliveryPersonId = closestPerson.getId();

        // Assign delivery person
        delivery.setDeliveryPersonId(deliveryPersonId);
        delivery.setStatus(DeliveryRequest.DeliveryReqStatus.APPROVED);
        DeliveryRequest savedDelivery = deliveryRepository.save(delivery);

        // Send notification - make sure this call is working
        try {
            logger.info("Sending notification for delivery {} to user {}", deliveryId, deliveryPersonId);
            notificationService.sendDeliveryAssignmentNotification(deliveryPersonId, savedDelivery);
        } catch (Exception e) {
            logger.error("Failed to send notification: {}", e.getMessage(), e);
        }

        return savedDelivery;
    }

    private List<User> findAvailableDeliveryPersons() {
        return userRepository.findByRolesInAndEnabled(
                        List.of(Role.ROLE_PROFESSIONAL, Role.ROLE_TEMPORARY),
                        true
                ).stream()
                .filter(User::isAvailable) // Using method reference instead of lambda
                .collect(Collectors.toList());
    }

    /**
     * Finds the delivery person closest to the pickup location
     */
    private User findClosestDeliveryPerson(List<User> deliveryPersons, LocationDTO pickupLocation) {
        return deliveryPersons.stream()
                .sorted(Comparator.comparingDouble(person ->
                        calculateDistance(
                                locationService.getLastKnownLocation(person.getId()),
                                pickupLocation
                        )
                ))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not determine closest delivery person"));
    }

    /**
     * Calculates distance between two locations using Haversine formula
     */
    private double calculateDistance(LocationDTO loc1, LocationDTO loc2) {
        if (loc1 == null || loc2 == null) {
            return Double.MAX_VALUE; // Handle case where location is unknown
        }

        final int R = 6371; // Earth's radius in km

        double latDistance = Math.toRadians(loc2.getLatitude() - loc1.getLatitude());
        double lonDistance = Math.toRadians(loc2.getLongitude() - loc1.getLongitude());

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(loc1.getLatitude())) * Math.cos(Math.toRadians(loc2.getLatitude()))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * Updates the status of a delivery
     */
    @Override
    public DeliveryRequest updateDeliveryStatus(String deliveryId, DeliveryRequest.DeliveryReqStatus newStatus, String deliveryPersonId) {
        DeliveryRequest delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + deliveryId));

        // Verify the delivery person is assigned to this delivery
        if (!delivery.getDeliveryPersonId().equals(deliveryPersonId)) {
            throw new IllegalStateException("Delivery person not assigned to this delivery");
        }

        // Validate status transitions
        validateStatusTransition(delivery.getStatus(), newStatus);

        delivery.setStatus(newStatus);
        return deliveryRepository.save(delivery);
    }

    /**
     * Validates if a status transition is allowed
     */
    private void validateStatusTransition(DeliveryRequest.DeliveryReqStatus currentStatus, DeliveryRequest.DeliveryReqStatus newStatus) {
        switch (currentStatus) {
            case PENDING:
                if (newStatus != DeliveryRequest.DeliveryReqStatus.APPROVED && newStatus != DeliveryRequest.DeliveryReqStatus.CANCELLED) {
                    throw new IllegalStateException("Invalid status transition from PENDING to " + newStatus);
                }
                break;
            case APPROVED:
                if (newStatus != DeliveryRequest.DeliveryReqStatus.IN_TRANSIT && newStatus != DeliveryRequest.DeliveryReqStatus.CANCELLED) {
                    throw new IllegalStateException("Invalid status transition from APPROVED to " + newStatus);
                }
                break;
            case IN_TRANSIT:
                if (newStatus != DeliveryRequest.DeliveryReqStatus.DELIVERED && newStatus != DeliveryRequest.DeliveryReqStatus.CANCELLED) {
                    throw new IllegalStateException("Invalid status transition from IN_TRANSIT to " + newStatus);
                }
                break;
            case DELIVERED:
                throw new IllegalStateException("Cannot change status once delivery is DELIVERED");
            case CANCELLED:
                throw new IllegalStateException("Cannot change status once delivery is CANCELLED");
        }
    }

    /**
     * Gets all deliveries assigned to a specific delivery person
     */
    @Override
    public List<DeliveryRequest> getDeliveriesForPerson(String deliveryPersonId) {
        return deliveryRepository.findByDeliveryPersonId(deliveryPersonId);
    }

    /**
     * Gets all pending deliveries that need assignment
     */
    @Override
    public List<DeliveryRequest> getPendingDeliveries() {
        return deliveryRepository.findByStatus(DeliveryRequest.DeliveryReqStatus.PENDING);
    }
}