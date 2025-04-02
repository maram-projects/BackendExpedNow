package com.example.ExpedNow.services;

import com.example.ExpedNow.dto.LocationDTO;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import com.example.ExpedNow.models.Delivery;
import com.example.ExpedNow.models.Role;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.repositories.DeliveryRepository;
import com.example.ExpedNow.repositories.UserRepository;
import com.example.ExpedNow.dto.DeliveryAssignmentDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DeliveryAssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryAssignmentService.class);

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private LocationService locationService;

    /**
     * Assigns a delivery to the closest available delivery person
     * @param deliveryId the delivery to assign
     * @return the updated delivery with assignment information
     */
    public Delivery assignDelivery(String deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery not found"));

        if (delivery.getStatus() != Delivery.DeliveryStatus.PENDING) {
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
        delivery.setStatus(Delivery.DeliveryStatus.APPROVED);
        Delivery savedDelivery = deliveryRepository.save(delivery);

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
                .filter(user -> user.isAvailable()) // Assuming User has an isAvailable field
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
    public Delivery updateDeliveryStatus(String deliveryId, Delivery.DeliveryStatus newStatus, String deliveryPersonId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
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
    private void validateStatusTransition(Delivery.DeliveryStatus currentStatus, Delivery.DeliveryStatus newStatus) {
        switch (currentStatus) {
            case PENDING:
                if (newStatus != Delivery.DeliveryStatus.APPROVED && newStatus != Delivery.DeliveryStatus.CANCELLED) {
                    throw new IllegalStateException("Invalid status transition from PENDING to " + newStatus);
                }
                break;
            case APPROVED:
                if (newStatus != Delivery.DeliveryStatus.IN_TRANSIT && newStatus != Delivery.DeliveryStatus.CANCELLED) {
                    throw new IllegalStateException("Invalid status transition from APPROVED to " + newStatus);
                }
                break;
            case IN_TRANSIT:
                if (newStatus != Delivery.DeliveryStatus.DELIVERED && newStatus != Delivery.DeliveryStatus.CANCELLED) {
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
    public List<Delivery> getDeliveriesForPerson(String deliveryPersonId) {
        return deliveryRepository.findByDeliveryPersonId(deliveryPersonId);
    }

    /**
     * Gets all pending deliveries that need assignment
     */
    public List<Delivery> getPendingDeliveries() {
        return deliveryRepository.findByStatus(Delivery.DeliveryStatus.PENDING);
    }
}