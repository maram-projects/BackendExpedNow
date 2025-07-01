package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.dto.LocationDTO;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import com.example.ExpedNow.models.ChatRoom;
import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.models.Mission;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.enums.PackageType;
import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.repositories.DeliveryReqRepository;
import com.example.ExpedNow.repositories.MissionRepository;
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
    private final MissionRepository missionRepository;
    private final NotificationServiceInterface notificationService;
    private final LocationServiceInterface locationService;
    private final AvailabilityServiceInterface availabilityService;
    private final ChatService chatService;

    @Autowired
    public DeliveryAssignmentServiceImpl(
            DeliveryReqRepository deliveryRepository,
            UserRepository userRepository,
            MissionRepository missionRepository,
            NotificationServiceInterface notificationService,
            LocationServiceInterface locationService,
            AvailabilityServiceInterface availabilityService,
            ChatService chatService) {
        this.deliveryRepository = deliveryRepository;
        this.userRepository = userRepository;
        this.missionRepository = missionRepository;
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
        if (delivery.getStatus() != DeliveryRequest.DeliveryReqStatus.PENDING) {
            logger.warn("Cannot assign delivery {} because status is {}, not PENDING",
                    deliveryId, delivery.getStatus());
            return delivery;
        }

        // Use scheduled date/time or current time if not scheduled
        LocalDateTime deliveryTime = delivery.getScheduledDate() != null ?
                delivery.getScheduledDate() : LocalDateTime.now();
        logger.info("Finding suitable delivery persons for delivery at time: {}", deliveryTime);

        // Find suitable delivery persons considering availability and mission status
        List<User> suitablePersons = findSuitableDeliveryPersons(delivery, deliveryTime);
        if (suitablePersons.isEmpty()) {
            logger.warn("No suitable delivery persons found for delivery {} at time {}",
                    deliveryId, deliveryTime);
            return delivery;
        }
        logger.info("Found {} suitable delivery persons", suitablePersons.size());

        // Get previous delivery person from the delivery itself
        String previousDeliveryPersonId = delivery.getDeliveryPersonId();

        // Get pickup location
        LocationDTO pickupLocation = new LocationDTO(
                delivery.getPickupLatitude(),
                delivery.getPickupLongitude()
        );

        // Use default location if pickup coordinates aren't set
        if (delivery.getPickupLatitude() == 0 && delivery.getPickupLongitude() == 0) {
            logger.info("Pickup location not set for delivery {}. Using default location", deliveryId);
            pickupLocation = new LocationDTO(0.0, 0.0);
        }

        // Pass previousDeliveryPersonId to best match finder
        User bestMatch = findBestMatchDeliveryPerson(suitablePersons, pickupLocation, previousDeliveryPersonId);
        logger.info("Best match found: {} - {}", bestMatch.getId(),
                bestMatch.getFirstName() + " " + bestMatch.getLastName());

        // ===== ADDED VALIDATION CHECK =====
        // Verify the selected delivery person is still available and has a schedule
        if (!bestMatch.isAvailable() || !availabilityService.hasExistingSchedule(bestMatch.getId())) {
            logger.error("Cannot assign delivery {} to user {}: User is unavailable or has no schedule",
                    deliveryId, bestMatch.getId());
            throw new IllegalStateException("Selected delivery person is no longer available or has no schedule");
        }
        // ===== END OF VALIDATION CHECK =====

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
        }

        // Create chat room if assignment successful
        if (savedDelivery.getDeliveryPersonId() != null && !savedDelivery.getDeliveryPersonId().isEmpty()) {
            try {
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
                .filter(User::isAvailable) // Must be marked as available
                .collect(Collectors.toList());

        logger.info("Found {} potential delivery persons before filtering", potentialDeliveryPersons.size());

        if (potentialDeliveryPersons.isEmpty()) {
            logger.warn("No active delivery persons found in the system");
            return Collections.emptyList();
        }

        List<User> availablePersons = potentialDeliveryPersons.stream()
                .filter(user -> {
                    try {
                        // 1. Must have an existing schedule
                        if (!availabilityService.hasExistingSchedule(user.getId())) {
                            logger.debug("User {} has no availability schedule", user.getId());
                            return false;
                        }

                        // 2. Check time availability
                        boolean availableOnDate = availabilityService.isUserAvailableAt(user.getId(), date, time);
                        if (!availableOnDate) {
                            availableOnDate = availabilityService.isUserAvailableAt(user.getId(), dayOfWeek, time);
                        }

                        if (!availableOnDate) {
                            logger.debug("User {} not available on {} at {}", user.getId(), date, time);
                        }
                        return availableOnDate;
                    } catch (Exception e) {
                        logger.error("Error checking availability for user {}: {}", user.getId(), e.getMessage());
                        return false; // Treat errors as unavailable
                    }
                })
                .filter(user -> canHandlePackageType(user, delivery.getPackageType()))
                .filter(user -> !hasExcessiveWorkload(user.getId()))
                .filter(user -> !hasActiveMissionInProgress(user.getId()))
                .collect(Collectors.toList());

        logger.info("After strict filtering, found {} available delivery persons", availablePersons.size());

        // Debug: Check missions for each user
        for (User user : potentialDeliveryPersons) {
            try {
                List<Mission> userMissions = missionRepository.findByDeliveryPersonIdAndStatusIn(
                        user.getId(),
                        Arrays.asList("IN_PROGRESS", "PENDING")
                );
                logger.debug("User {} has {} active mission(s)", user.getId(), userMissions.size());
            } catch (Exception e) {
                logger.debug("Could not check missions for user {}: {}", user.getId(), e.getMessage());
            }
        }

        // If no one found, use relaxed constraints (but still require schedule and availability flag)
        if (availablePersons.isEmpty() && !potentialDeliveryPersons.isEmpty()) {
            logger.warn("No delivery persons available after strict filtering. Relaxing time constraints.");

            availablePersons = potentialDeliveryPersons.stream()
                    .filter(user -> user.isAvailable()) // Must be available (flag)
                    .filter(user -> {
                        try {
                            return availabilityService.hasExistingSchedule(user.getId()); // Must have schedule
                        } catch (Exception e) {
                            logger.error("Error checking schedule: {}", e.getMessage());
                            return false;
                        }
                    })
                    .filter(user -> canHandlePackageType(user, delivery.getPackageType()))
                    .filter(user -> !hasActiveMissionInProgress(user.getId()))
                    .limit(3)
                    .collect(Collectors.toList());

            logger.info("After relaxing constraints, found {} delivery persons with schedules", availablePersons.size());
        }

        return availablePersons;
    }    /**
     * Check if delivery person has any active missions in progress
     * Only considers missions that are truly active (IN_PROGRESS or PENDING)
     * Completed missions should not block new assignments
     */
    private boolean hasActiveMissionInProgress(String userId) {
        try {
            // Only check for truly active statuses - exclude COMPLETED missions
            List<Mission> activeMissions = missionRepository.findByDeliveryPersonIdAndStatusIn(
                    userId,
                    Arrays.asList("IN_PROGRESS", "PENDING") // Removed "COMPLETED" from here
            );

            boolean hasActiveMission = !activeMissions.isEmpty();

            if (hasActiveMission) {
                logger.debug("User {} has {} active mission(s) in progress/pending",
                        userId, activeMissions.size());

                // Log mission details for debugging
                for (Mission mission : activeMissions) {
                    logger.debug("Active mission for user {}: ID={}, Status={}, DeliveryId={}",
                            userId, mission.getId(), mission.getStatus(),
                            mission.getDeliveryRequest().getId());
                }
            } else {
                logger.debug("User {} has no active missions - available for new assignments", userId);
            }

            return hasActiveMission;
        } catch (Exception e) {
            logger.error("Error checking active missions for user {}: {}", userId, e.getMessage());
            // In case of error, assume no active missions to avoid blocking assignments
            return false;
        }
    }

    private boolean hasExcessiveWorkload(String userId) {
        final int MAX_ACTIVE_DELIVERIES = 5;
        List<DeliveryRequest> activeDeliveries = deliveryRepository.findActiveDeliveriesByDeliveryPerson(userId);
        boolean excessive = activeDeliveries.size() >= MAX_ACTIVE_DELIVERIES;
        if (excessive) {
            logger.debug("User {} has excessive workload: {} active deliveries", userId, activeDeliveries.size());
        }
        return excessive;
    }

    private boolean canHandlePackageType(User user, PackageType packageType) {
        if (packageType == null) {
            return true;
        }

        if (user.getVehicleType() == null) {
            logger.debug("User {} has no vehicle type, assuming can handle only small packages", user.getId());
            return packageType == PackageType.SMALL || packageType == PackageType.FRAGILE;
        }

        return switch (packageType) {
            case SMALL, FRAGILE -> true;
            case MEDIUM -> user.getVehicleType().getMaxWeight() >= 10;
            case LARGE -> user.getVehicleType().getMaxWeight() >= 20;
            case HEAVY -> user.getVehicleType().getMaxWeight() >= 50;
        };
    }

    private User findBestMatchDeliveryPerson(List<User> deliveryPersons, LocationDTO pickupLocation, String previousDeliveryPersonId) {
        if (deliveryPersons.isEmpty()) {
            throw new IllegalArgumentException("No delivery persons available");
        }
        if (deliveryPersons.size() == 1) {
            logger.info("Only one delivery person available, selecting them automatically");
            return deliveryPersons.get(0);
        }

        final double DISTANCE_WEIGHT = 0.4;
        final double WORKLOAD_WEIGHT = 0.3;
        final double RATING_WEIGHT = 0.2;
        final double PREVIOUS_BOOST = 0.1; // Boost for previous delivery person

        boolean hasPrevious = previousDeliveryPersonId != null && !previousDeliveryPersonId.isEmpty();

        Map<User, Double> scores = new HashMap<>();
        double maxDistance = 0.1;
        int maxWorkload = 0;
        double maxRating = 5.0;

        Map<User, Double> distances = new HashMap<>();
        for (User person : deliveryPersons) {
            LocationDTO personLocation = locationService.getLastKnownLocation(person.getId());
            if (personLocation == null) {
                logger.debug("No location data for user {}, using default distance", person.getId());
                distances.put(person, 10.0);
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
                distanceScore = maxDistance > 0.1 ? 1 - (distances.get(person) / maxDistance) : 0.5;
            }
            double workloadScore = maxWorkload == 0 ? 1 : 1 - ((double) workloads.get(person) / maxWorkload);
            double ratingScore = (person.getRating() > 0 && maxRating > 0)
                    ? person.getRating() / maxRating
                    : 0.5;

            double totalScore = (distanceScore * DISTANCE_WEIGHT) +
                    (workloadScore * WORKLOAD_WEIGHT) +
                    (ratingScore * RATING_WEIGHT);

            // Boost score if this is the previous delivery person
            if (hasPrevious && person.getId().equals(previousDeliveryPersonId)) {
                totalScore += PREVIOUS_BOOST;
            }

            scores.put(person, totalScore);
        }

        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new IllegalStateException("Could not determine best delivery person"));
    }    private double calculateDistance(LocationDTO loc1, LocationDTO loc2) {
        if (loc1 == null || loc2 == null) {
            return Double.MAX_VALUE;
        }

        if ((loc1.getLatitude() == 0 && loc1.getLongitude() == 0) ||
                (loc2.getLatitude() == 0 && loc2.getLongitude() == 0)) {
            return 10.0;
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

        DeliveryRequest.DeliveryReqStatus previousStatus = delivery.getStatus();
        delivery.setStatus(newStatus);

        switch (newStatus) {
            case APPROVED:
                break;
            case IN_TRANSIT:
                delivery.setStartedAt(LocalDateTime.now());
                // Auto-complete mission when delivery status changes to IN_TRANSIT
                autoCompleteMissionOnInTransit(deliveryId, deliveryPersonId);
                break;
            case DELIVERED:
                delivery.setCompletedAt(LocalDateTime.now());
                User deliveryPerson = userRepository.findById(deliveryPersonId)
                        .orElseThrow(() -> new ResourceNotFoundException("Delivery person not found"));
                deliveryPerson.updateRating(5.0);
                deliveryPerson.setTotalDeliveries(deliveryPerson.getTotalDeliveries() + 1);
                userRepository.save(deliveryPerson);
                // Complete mission on delivery completion
                autoCompleteMissionOnDelivered(deliveryId, deliveryPersonId);
                break;
            default:
                break;
        }

        return deliveryRepository.save(delivery);
    }

    /**
     * Auto-complete mission when delivery status changes to IN_TRANSIT
     */
    private void autoCompleteMissionOnInTransit(String deliveryId, String deliveryPersonId) {
        try {
            Optional<Mission> missionOpt = missionRepository.findByDeliveryRequestId(deliveryId);

            if (missionOpt.isPresent()) {
                Mission mission = missionOpt.get();

                // Only complete if mission is in IN_PROGRESS status
                if ("IN_PROGRESS".equals(mission.getStatus())) {
                    logger.info("Auto-completing mission {} for delivery {} when status changed to IN_TRANSIT",
                            mission.getId(), deliveryId);

                    mission.setStatus("COMPLETED");
                    mission.setEndTime(LocalDateTime.now());
                    missionRepository.save(mission);

                    logger.info("Mission {} successfully completed automatically", mission.getId());
                } else {
                    logger.debug("Mission {} for delivery {} is not in IN_PROGRESS status (current: {}), skipping auto-completion",
                            mission.getId(), deliveryId, mission.getStatus());
                }
            } else {
                logger.debug("No mission found for delivery {} to auto-complete", deliveryId);
            }
        } catch (Exception e) {
            logger.error("Error auto-completing mission for delivery {}: {}", deliveryId, e.getMessage(), e);
            // Don't throw exception - this shouldn't break delivery status update
        }
    }

    /**
     * Complete mission when delivery is fully delivered (backup method)
     */
    private void autoCompleteMissionOnDelivered(String deliveryId, String deliveryPersonId) {
        try {
            Optional<Mission> missionOpt = missionRepository.findByDeliveryRequestId(deliveryId);

            if (missionOpt.isPresent()) {
                Mission mission = missionOpt.get();

                // Complete mission if not already completed
                if (!"COMPLETED".equals(mission.getStatus()) && !"CANCELLED".equals(mission.getStatus())) {
                    logger.info("Auto-completing mission {} for delivery {} on final delivery",
                            mission.getId(), deliveryId);

                    mission.setStatus("COMPLETED");
                    mission.setEndTime(LocalDateTime.now());
                    missionRepository.save(mission);

                    logger.info("Mission {} completed on delivery completion", mission.getId());
                }
            }
        } catch (Exception e) {
            logger.error("Error completing mission on delivery completion for delivery {}: {}", deliveryId, e.getMessage(), e);
        }
    }

    private void validateStatusTransition(DeliveryRequest.DeliveryReqStatus currentStatus, DeliveryRequest.DeliveryReqStatus newStatus) {
        switch (currentStatus) {
            case ASSIGNED:
                if (newStatus != DeliveryRequest.DeliveryReqStatus.PENDING &&
                        newStatus != DeliveryRequest.DeliveryReqStatus.APPROVED &&
                        newStatus != DeliveryRequest.DeliveryReqStatus.CANCELLED) {
                    throw new IllegalStateException("Invalid status transition from ASSIGNED to " + newStatus);
                }
                break;
            case PENDING:
                if (newStatus != DeliveryRequest.DeliveryReqStatus.APPROVED &&
                        newStatus != DeliveryRequest.DeliveryReqStatus.CANCELLED) {
                    throw new IllegalStateException("Invalid status transition from PENDING to " + newStatus);
                }
                break;
            case APPROVED:
                if (newStatus != DeliveryRequest.DeliveryReqStatus.IN_TRANSIT &&
                        newStatus != DeliveryRequest.DeliveryReqStatus.CANCELLED) {
                    throw new IllegalStateException("Invalid status transition from APPROVED to " + newStatus);
                }
                break;
            case IN_TRANSIT:
                if (newStatus != DeliveryRequest.DeliveryReqStatus.DELIVERED &&
                        newStatus != DeliveryRequest.DeliveryReqStatus.CANCELLED) {
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