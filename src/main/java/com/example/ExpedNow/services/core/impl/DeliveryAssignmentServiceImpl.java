package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.dto.LocationDTO;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import com.example.ExpedNow.models.ChatRoom;
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
import java.time.LocalDate;
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
    private final ChatService chatService; // Add this dependency

    @Autowired
    public DeliveryAssignmentServiceImpl(
            DeliveryReqRepository deliveryRepository,
            UserRepository userRepository,
            NotificationServiceInterface notificationService,
            LocationServiceInterface locationService,
            AvailabilityServiceInterface availabilityService, ChatService chatService) {
        this.deliveryRepository = deliveryRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.locationService = locationService;
        this.availabilityService = availabilityService;
        this.chatService = chatService;
    }

    @Override
    public DeliveryRequest assignDelivery(String deliveryId) {
        logger.info("Starting assignment process for delivery ID: {}", deliveryId);

        DeliveryRequest delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found"));

        // Skip if already assigned
        if (delivery.getDeliveryPersonId() != null && !delivery.getDeliveryPersonId().isEmpty()) {
            logger.info("Delivery {} already has a delivery person assigned: {}. Skipping assignment.",
                    deliveryId, delivery.getDeliveryPersonId());
            return delivery;
        }

        // Don't restrict by status - allow assignment for PENDING deliveries
        // This was preventing newly created deliveries from being assigned
        if (delivery.getStatus() != DeliveryRequest.DeliveryReqStatus.PENDING) {
            logger.warn("Cannot assign delivery {} because status is {}, not PENDING",
                    deliveryId, delivery.getStatus());
            return delivery;
        }

        // Use scheduled date/time or current time if not scheduled
        LocalDateTime deliveryTime = delivery.getScheduledDate() != null ?
                delivery.getScheduledDate() : LocalDateTime.now();

        logger.info("Finding suitable delivery persons for delivery at time: {}", deliveryTime);

        // Find suitable delivery persons considering availability
        List<User> suitablePersons = findSuitableDeliveryPersons(delivery, deliveryTime);

        if (suitablePersons.isEmpty()) {
            logger.warn("No suitable delivery persons found for delivery {} at time {}",
                    deliveryId, deliveryTime);
            return delivery;
        }

        logger.info("Found {} suitable delivery persons", suitablePersons.size());

        // Log the IDs of suitable persons for debugging
        for (User person : suitablePersons) {
            logger.debug("Suitable delivery person: {} - {}", person.getId(),
                    person.getFirstName() + " " + person.getLastName());
        }

        // Find the best match based on location and other factors
        LocationDTO pickupLocation = new LocationDTO(
                delivery.getPickupLatitude(),
                delivery.getPickupLongitude()
        );

        // IMPORTANT FIX: Use default location if pickup coordinates aren't set
        if (delivery.getPickupLatitude() == 0 && delivery.getPickupLongitude() == 0) {
            logger.info("Pickup location not set for delivery {}. Using default location", deliveryId);
            // Set to a default location or get from address geocoding if available
            // For now, using a dummy location
            pickupLocation = new LocationDTO(0.0, 0.0);
        }

        User bestMatch = findBestMatchDeliveryPerson(suitablePersons, pickupLocation);
        logger.info("Best match found: {} - {}", bestMatch.getId(),
                bestMatch.getFirstName() + " " + bestMatch.getLastName());

        // Assign the delivery
        delivery.setStatus(DeliveryRequest.DeliveryReqStatus.ASSIGNED);
        delivery.setDeliveryPersonId(bestMatch.getId());
        delivery.setAssignedAt(LocalDateTime.now());
        DeliveryRequest savedDelivery = deliveryRepository.save(delivery);
        logger.info("Delivery {} successfully assigned to {}", deliveryId, bestMatch.getId());

        // Send notification
        try {
            String fullName = bestMatch.getFirstName() + " " + bestMatch.getLastName();
            logger.info("Sending assignment request for delivery {} to user {} (Name: {})",
                    deliveryId, bestMatch.getId(), fullName);

            notificationService.sendAssignmentRequestNotification(
                    bestMatch.getId(),
                    savedDelivery
            );
        } catch (Exception e) {
            logger.error("Failed to send notification: {}", e.getMessage(), e);
            // Continue even if notification fails - at least assignment was successful
        }
        if (savedDelivery.getDeliveryPersonId() != null && !savedDelivery.getDeliveryPersonId().isEmpty()) {
            try {
                // Get or create chat room
                ChatRoom chatRoom = chatService.getOrCreateChatRoom(
                        savedDelivery.getId(),
                        savedDelivery.getClientId(),
                        savedDelivery.getDeliveryPersonId()
                );

                logger.info("Chat room created for delivery {} between client {} and delivery person {}",
                        savedDelivery.getId(),
                        savedDelivery.getClientId(),
                        savedDelivery.getDeliveryPersonId());
            } catch (Exception e) {
                logger.error("Failed to create chat room for delivery {}: {}", savedDelivery.getId(), e.getMessage());
                // Continue even if chat room creation fails
            }
        }

        return savedDelivery;
    }

    private List<User> findSuitableDeliveryPersons(DeliveryRequest delivery, LocalDateTime deliveryTime) {
        // Get day and time for availability check
        DayOfWeek dayOfWeek = deliveryTime.getDayOfWeek();
        LocalTime time = deliveryTime.toLocalTime();
        LocalDate date = deliveryTime.toLocalDate();

        // Get all active delivery persons
        List<User> potentialDeliveryPersons = userRepository.findByRolesInAndEnabled(
                        List.of(Role.ROLE_PROFESSIONAL, Role.ROLE_DELIVERY_PERSON, Role.ROLE_TEMPORARY),
                        true
                ).stream()
                .filter(User::isAvailable)
                .collect(Collectors.toList());

        logger.info("Found {} potential delivery persons before filtering", potentialDeliveryPersons.size());

        if (potentialDeliveryPersons.isEmpty()) {
            logger.warn("No active delivery persons found in the system. Check user repository and roles configuration.");
            long totalUsers = userRepository.count();
            logger.info("Total users in the database: {}", totalUsers);

            if (totalUsers > 0) {
                List<User> anyActiveUsers = userRepository.findByRolesInAndEnabled(
                        List.of(Role.ROLE_PROFESSIONAL, Role.ROLE_DELIVERY_PERSON, Role.ROLE_TEMPORARY),
                        true
                );
                logger.info("Found {} active users regardless of role", anyActiveUsers.size());

                if (!anyActiveUsers.isEmpty()) {
                    logger.warn("EMERGENCY MODE: Using any active user as delivery person!");
                    return anyActiveUsers.subList(0, Math.min(3, anyActiveUsers.size()));
                }
            }
        }

        List<User> availablePersons = potentialDeliveryPersons.stream()
                .filter(user -> {
                    try {
                        // First check specific date availability
                        boolean availableOnDate = availabilityService.isUserAvailableAt(user.getId(), date, time);

                        // If not specifically available on this date, check weekly pattern
                        if (!availableOnDate) {
                            availableOnDate = availabilityService.isUserAvailableAt(user.getId(), dayOfWeek, time);
                        }

                        // If still not available, check if user has any schedule at all
                        if (!availableOnDate) {
                            try {
                                boolean hasSchedule = availabilityService.hasExistingSchedule(user.getId());
                                if (!hasSchedule) {
                                    logger.info("User {} has no availability schedule, assuming available", user.getId());
                                    return true;
                                }
                            } catch (Exception e) {
                                logger.warn("Error checking if user has schedule: {}, assuming available", e.getMessage());
                                return true;
                            }
                        }

                        if (!availableOnDate) {
                            logger.debug("User {} not available on {} at {}",
                                    user.getId(), date, time);
                        }
                        return availableOnDate;
                    } catch (Exception e) {
                        logger.error("Error checking availability for user {}: {}", user.getId(), e.getMessage());
                        return true;
                    }
                })
                .filter(user -> canHandlePackageType(user, delivery.getPackageType()))
                .filter(user -> !hasExcessiveWorkload(user.getId()))
                .collect(Collectors.toList());

        logger.info("After filtering, found {} available delivery persons", availablePersons.size());

        if (availablePersons.isEmpty() && !potentialDeliveryPersons.isEmpty()) {
            logger.warn("No delivery persons available after filtering. Relaxing constraints.");

            availablePersons = potentialDeliveryPersons.stream()
                    .filter(user -> canHandlePackageType(user, delivery.getPackageType()))
                    .limit(3)
                    .collect(Collectors.toList());

            logger.info("After relaxing constraints, found {} delivery persons", availablePersons.size());
        }

        return availablePersons;
    }
    private boolean hasExcessiveWorkload(String userId) {
        final int MAX_ACTIVE_DELIVERIES = 5; // IMPORTANT FIX: Increased from 3 to 5
        List<DeliveryRequest> activeDeliveries = deliveryRepository.findActiveDeliveriesByDeliveryPerson(userId);
        boolean excessive = activeDeliveries.size() >= MAX_ACTIVE_DELIVERIES;
        if (excessive) {
            logger.debug("User {} has excessive workload: {} active deliveries", userId, activeDeliveries.size());
        }
        return excessive;
    }

    private boolean canHandlePackageType(User user, PackageType packageType) {
        // IMPORTANT FIX: Handle null cases better
        if (packageType == null) {
            return true;
        }

        if (user.getVehicleType() == null) {
            logger.debug("User {} has no vehicle type, assuming can handle only small packages", user.getId());
            // If no vehicle type, assume can only handle small packages
            return packageType == PackageType.SMALL || packageType == PackageType.FRAGILE;
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

        // IMPORTANT FIX: If only one person is available, return them immediately
        if (deliveryPersons.size() == 1) {
            logger.info("Only one delivery person available, selecting them automatically");
            return deliveryPersons.get(0);
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

            // IMPORTANT FIX: Handle case when location service returns null
            if (personLocation == null) {
                logger.debug("No location data for user {}, using default distance", person.getId());
                distances.put(person, 10.0); // Use a default medium distance instead of MAX_VALUE
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
                // IMPORTANT FIX: Avoid division by zero
                distanceScore = maxDistance > 0.1 ? 1 - (distances.get(person) / maxDistance) : 0.5;
            }

            double workloadScore = maxWorkload == 0 ? 1 : 1 - ((double) workloads.get(person) / maxWorkload);

            // IMPORTANT FIX: Handle null or zero rating
            double ratingScore = (person.getRating() > 0 && maxRating > 0)
                    ? person.getRating() / maxRating
                    : 0.5;

            double totalScore = (distanceScore * DISTANCE_WEIGHT) +
                    (workloadScore * WORKLOAD_WEIGHT) +
                    (ratingScore * RATING_WEIGHT);

            logger.debug("Score for user {}: {} (distance: {}, workload: {}, rating: {})",
                    person.getId(), totalScore, distanceScore, workloadScore, ratingScore);

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

        // IMPORTANT FIX: Handle case where coordinates are all zeros (default values)
        if ((loc1.getLatitude() == 0 && loc1.getLongitude() == 0) ||
                (loc2.getLatitude() == 0 && loc2.getLongitude() == 0)) {
            return 10.0; // Return a default medium distance instead of calculating
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
            case ASSIGNED:
                // Allow transitioning back to PENDING on rejection
                if (newStatus != DeliveryRequest.DeliveryReqStatus.PENDING &&
                        newStatus != DeliveryRequest.DeliveryReqStatus.APPROVED &&
                        newStatus != DeliveryRequest.DeliveryReqStatus.CANCELLED) {
                    throw new IllegalStateException("Invalid status transition from ASSIGNED to " + newStatus);
                }
                break;
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

    @Override
    public List<DeliveryRequest> getDeliveriesForPerson(String deliveryPersonId) {
        return deliveryRepository.findByDeliveryPersonId(deliveryPersonId);
    }

    @Override
    public List<DeliveryRequest> getPendingDeliveries() {
        return deliveryRepository.findByStatus(DeliveryRequest.DeliveryReqStatus.PENDING);
    }
}