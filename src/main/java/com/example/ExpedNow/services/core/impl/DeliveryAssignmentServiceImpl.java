package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.dto.LocationDTO;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.enums.PackageType;
import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.repositories.DeliveryReqRepository;
import com.example.ExpedNow.repositories.UserRepository;
import com.example.ExpedNow.services.core.AvailabilityServiceInterface;
import com.example.ExpedNow.services.core.DeliveryAssignmentServiceInterface;
import com.example.ExpedNow.services.core.LocationServiceInterface;
import com.example.ExpedNow.services.core.NotificationServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Primary
public class DeliveryAssignmentServiceImpl implements DeliveryAssignmentServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryAssignmentServiceImpl.class);

    private final DeliveryReqRepository deliveryRepository;
    private final UserRepository userRepository;
    private final NotificationServiceInterface notificationService;
    private final LocationServiceInterface locationService;
    private final AvailabilityServiceInterface availabilityService;

    @Autowired
    public DeliveryAssignmentServiceImpl(
            DeliveryReqRepository deliveryRepository,
            UserRepository userRepository,
            NotificationServiceInterface notificationService,
            LocationServiceInterface locationService,
            AvailabilityServiceInterface availabilityService) {
        this.deliveryRepository = deliveryRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.locationService = locationService;
        this.availabilityService = availabilityService;
    }

    @Override
    public DeliveryRequest assignDelivery(String deliveryId) {
        DeliveryRequest delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found"));

        if (delivery.getDeliveryPersonId() != null && !delivery.getDeliveryPersonId().isEmpty()) {
            logger.info("Delivery {} already has a delivery person assigned: {}. Skipping assignment.",
                    deliveryId, delivery.getDeliveryPersonId());
            return delivery;
        }

        if (delivery.getStatus() != DeliveryRequest.DeliveryReqStatus.PENDING) {
            throw new IllegalStateException("Only pending deliveries can be assigned");
        }

        LocalDateTime deliveryTime = delivery.getScheduledDate() != null ?
                delivery.getScheduledDate() : LocalDateTime.now();

        List<User> suitablePersons = findSuitableDeliveryPersons(delivery, deliveryTime);

        if (suitablePersons.isEmpty()) {
            logger.warn("No suitable delivery persons found for delivery {} at time {}",
                    deliveryId, deliveryTime);
            return delivery;
        }

        LocationDTO pickupLocation = new LocationDTO(
                delivery.getPickupLatitude(),
                delivery.getPickupLongitude()
        );

        User bestMatch = findBestMatchDeliveryPerson(suitablePersons, pickupLocation);

        delivery.setStatus(DeliveryRequest.DeliveryReqStatus.ASSIGNED);
        delivery.setDeliveryPersonId(bestMatch.getId());
        delivery.setAssignedAt(LocalDateTime.now());
        DeliveryRequest savedDelivery = deliveryRepository.save(delivery);

        try {
            String fullName = bestMatch.getFirstName() + " " + bestMatch.getLastName();
            logger.info("Sending assignment request for delivery {} to user {} (Name: {})",
                    deliveryId, bestMatch.getId(), fullName);

            notificationService.sendAssignmentRequestNotification(
                    bestMatch.getId(),
                    savedDelivery
            );
        } catch (Exception e) {
            logger.error("Failed to send notification: {}", e.getMessage());
        }

        return savedDelivery;
    }

    private List<User> findSuitableDeliveryPersons(DeliveryRequest delivery, LocalDateTime deliveryTime) {
        DayOfWeek dayOfWeek = deliveryTime.getDayOfWeek();
        LocalTime time = deliveryTime.toLocalTime();

        List<User> potentialDeliveryPersons = userRepository.findByRolesInAndEnabled(
                        List.of(Role.ROLE_PROFESSIONAL, Role.ROLE_DELIVERY_PERSON, Role.ROLE_TEMPORARY),
                        true
                ).stream()
                .filter(User::isAvailable)
                .collect(Collectors.toList());

        return potentialDeliveryPersons.stream()
                .filter(user -> {
                    try {
                        boolean available = availabilityService.isUserAvailableAt(user.getId(), dayOfWeek, time);
                        if (!available) {
                            logger.debug("User {} not available on {} at {}",
                                    user.getId(), dayOfWeek, time);
                        }
                        return available;
                    } catch (Exception e) {
                        logger.error("Error checking availability for user {}", user.getId(), e);
                        return false;
                    }
                })
                .filter(user -> canHandlePackageType(user, delivery.getPackageType()))
                .filter(user -> !hasExcessiveWorkload(user.getId()))
                .collect(Collectors.toList());
    }

    private boolean hasExcessiveWorkload(String userId) {
        final int MAX_ACTIVE_DELIVERIES = 3;
        List<DeliveryRequest> activeDeliveries = deliveryRepository.findActiveDeliveriesByDeliveryPerson(userId);
        return activeDeliveries.size() >= MAX_ACTIVE_DELIVERIES;
    }

    private boolean canHandlePackageType(User user, PackageType packageType) {
        if (packageType == null || user.getVehicleType() == null) {
            return true;
        }

        return switch (packageType) {
            case SMALL, FRAGILE -> true;
            case MEDIUM -> user.getVehicleType().getMaxWeight() >= 10;
            case LARGE -> user.getVehicleType().getMaxWeight() >= 20;
            case HEAVY -> user.getVehicleType().getMaxWeight() >= 50;
        };
    }

    private User findBestMatchDeliveryPerson(List<User> deliveryPersons, LocationDTO pickupLocation) {
        if (deliveryPersons.isEmpty()) {
            throw new IllegalArgumentException("No delivery persons available");
        }

        final double DISTANCE_WEIGHT = 0.4;
        final double WORKLOAD_WEIGHT = 0.4;
        final double RATING_WEIGHT = 0.2;

        Map<User, Double> scores = new HashMap<>();
        double maxDistance = 0.1;
        int maxWorkload = 0;
        double maxRating = 5.0;

        Map<User, Double> distances = new HashMap<>();
        for (User person : deliveryPersons) {
            LocationDTO personLocation = locationService.getLastKnownLocation(person.getId());
            if (personLocation == null) {
                distances.put(person, Double.MAX_VALUE);
                continue;
            }

            double distance = calculateDistance(personLocation, pickupLocation);
            distances.put(person, distance);
            maxDistance = Math.max(maxDistance, distance);
        }

        Map<User, Integer> workloads = new HashMap<>();
        for (User person : deliveryPersons) {
            List<DeliveryRequest> activeDeliveries = deliveryRepository.findActiveDeliveriesByDeliveryPerson(person.getId());
            int workload = activeDeliveries.size();
            workloads.put(person, workload);
            maxWorkload = Math.max(maxWorkload, workload);
        }

        for (User person : deliveryPersons) {
            double distanceScore = 0;
            if (distances.get(person) < Double.MAX_VALUE) {
                distanceScore = 1 - (distances.get(person) / maxDistance);
            }

            double workloadScore = maxWorkload == 0 ? 1 : 1 - ((double) workloads.get(person) / maxWorkload);
            double ratingScore = person.getRating() / maxRating;

            double totalScore = (distanceScore * DISTANCE_WEIGHT) +
                    (workloadScore * WORKLOAD_WEIGHT) +
                    (ratingScore * RATING_WEIGHT);

            scores.put(person, totalScore);
        }

        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new IllegalStateException("Could not determine best delivery person"));
    }

    private double calculateDistance(LocationDTO loc1, LocationDTO loc2) {
        if (loc1 == null || loc2 == null) {
            return Double.MAX_VALUE;
        }

        final int R = 6371;

        double latDistance = Math.toRadians(loc2.getLatitude() - loc1.getLatitude());
        double lonDistance = Math.toRadians(loc2.getLongitude() - loc1.getLongitude());

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(loc1.getLatitude())) * Math.cos(Math.toRadians(loc2.getLatitude()))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    @Override
    public DeliveryRequest updateDeliveryStatus(String deliveryId, DeliveryRequest.DeliveryReqStatus newStatus, String deliveryPersonId) {
        DeliveryRequest delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + deliveryId));

        if (!delivery.getDeliveryPersonId().equals(deliveryPersonId)) {
            throw new IllegalStateException("Delivery person not assigned to this delivery");
        }

        validateStatusTransition(delivery.getStatus(), newStatus);

        delivery.setStatus(newStatus);

        switch (newStatus) {
            case APPROVED:
                break;
            case IN_TRANSIT:
                delivery.setStartedAt(LocalDateTime.now());
                break;
            case DELIVERED:
                delivery.setCompletedAt(LocalDateTime.now());
                User deliveryPerson = userRepository.findById(deliveryPersonId)
                        .orElseThrow(() -> new ResourceNotFoundException("Delivery person not found"));
                deliveryPerson.updateRating(5.0);
                deliveryPerson.setTotalDeliveries(deliveryPerson.getTotalDeliveries() + 1);
                userRepository.save(deliveryPerson);
                break;
            default:
                break;
        }

        return deliveryRepository.save(delivery);
    }

    private void validateStatusTransition(DeliveryRequest.DeliveryReqStatus currentStatus, DeliveryRequest.DeliveryReqStatus newStatus) {
        switch (currentStatus) {
            case PENDING:
                if (newStatus != DeliveryRequest.DeliveryReqStatus.APPROVED && newStatus != DeliveryRequest.DeliveryReqStatus.CANCELLED) {
                    throw new IllegalStateException("Invalid status transition from PENDING to " + newStatus);
                }
                break;
            case ASSIGNED:
                if (newStatus != DeliveryRequest.DeliveryReqStatus.APPROVED && newStatus != DeliveryRequest.DeliveryReqStatus.CANCELLED) {
                    throw new IllegalStateException("Invalid status transition from ASSIGNED to " + newStatus);
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

    @Override
    public List<DeliveryRequest> getDeliveriesForPerson(String deliveryPersonId) {
        return deliveryRepository.findByDeliveryPersonId(deliveryPersonId);
    }

    @Override
    public List<DeliveryRequest> getPendingDeliveries() {
        return deliveryRepository.findByStatus(DeliveryRequest.DeliveryReqStatus.PENDING);
    }
}