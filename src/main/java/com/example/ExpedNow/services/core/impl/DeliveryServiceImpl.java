package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.dto.DeliveryResponseDTO;
import com.example.ExpedNow.dto.ImageAnalysisResponse;
import com.example.ExpedNow.dto.UserDTO;
import com.example.ExpedNow.dto.VehicleDTO;
import com.example.ExpedNow.models.ChatRoom;
import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.models.Mission;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.Vehicle;
import com.example.ExpedNow.models.enums.PaymentStatus;
import com.example.ExpedNow.repositories.DeliveryReqRepository;
import com.example.ExpedNow.repositories.MissionRepository;
import com.example.ExpedNow.services.core.*;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
@Primary
public class DeliveryServiceImpl implements DeliveryServiceInterface {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryServiceImpl.class);

    private final UserServiceInterface userService;
    private final ChatService chatService;
    private final DeliveryReqRepository deliveryRepository;
    private final VehicleServiceInterface vehicleService;
    private final MissionRepository missionRepository;

    @Autowired
    private ImageAnalysisService imageAnalysisService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private DeliveryPricingService pricingService;

    @Autowired
    private DeliveryAssignmentServiceImpl deliveryAssignmentService;

    @Autowired
    private ObjectMapper objectMapper;

    public DeliveryServiceImpl(UserServiceInterface userService,
                               ChatService chatService,
                               DeliveryReqRepository deliveryRepository,
                               VehicleServiceInterface vehicleService,
                               MissionRepository missionRepository) {
        this.userService = userService;
        this.chatService = chatService;
        this.deliveryRepository = deliveryRepository;
        this.vehicleService = vehicleService;
        this.missionRepository = missionRepository;
    }

    // ... (all your existing methods remain the same) ...

    /**
     * Create delivery request with image analysis
     */
    public DeliveryRequest createDeliveryWithImage(DeliveryRequest delivery, MultipartFile imageFile) {
        try {
            String imagePath = fileStorageService.storeFile(imageFile);
            delivery.setPackageImageUrl(imagePath);

            ImageAnalysisResponse analysis = imageAnalysisService.analyzeImage(imageFile);

            if (analysis.isSuccess()) {
                // Store analysis as JSON
                delivery.setPackageImageAnalysis(objectMapper.writeValueAsString(analysis));

                String extractedText = analysis.getAnalysis().getTextExtraction().getFullText();
                delivery.setExtractedText(extractedText);
                delivery.setImageQuality(analysis.getDeliveryRelevantInfo().getImageQuality());
                delivery.setImageAnalyzed(true);
                delivery.setImageAnalyzedAt(LocalDateTime.now());

                // Append to description only if text exists
                if (StringUtils.hasText(extractedText)) {
                    delivery.setPackageDescription(
                            delivery.getPackageDescription() +
                                    "\n\nExtracted text from image: " + extractedText
                    );
                }
                logger.info("Image analysis successful for delivery: {}", delivery.getId());
            } else {
                logger.warn("Image analysis failed: {}", analysis.getError());
            }
        } catch (Exception e) {
            logger.error("Error in image analysis: {}", e.getMessage());
        }
        return createDelivery(delivery);
    }


    /**
     * Re-analyze image for existing delivery request
     */
    public DeliveryRequest reanalyzeImage(String deliveryId, MultipartFile newImageFile) {
        DeliveryRequest delivery = getDeliveryById(deliveryId);

        try {
            // Store the new image
            String imagePath = fileStorageService.storeFile(newImageFile);
            delivery.setPackageImageUrl(imagePath);

            // Analyze the new image
            ImageAnalysisResponse analysis = imageAnalysisService.analyzeImage(newImageFile);

            if (analysis.isSuccess()) {
                delivery.setPackageImageAnalysis(objectMapper.writeValueAsString(analysis));
                delivery.setExtractedText(analysis.getAnalysis().getTextExtraction().getFullText());
                delivery.setImageQuality(analysis.getDeliveryRelevantInfo().getImageQuality());
                delivery.setImageAnalyzed(true);
                delivery.setImageAnalyzedAt(LocalDateTime.now());

                logger.info("Image reanalysis successful for delivery: {}", deliveryId);
            }

        } catch (Exception e) {
            logger.error("Error in image reanalysis: {}", e.getMessage());
            throw new RuntimeException("Failed to reanalyze image", e);
        }

        return deliveryRepository.save(delivery);
    }

    /**
     * Get image analysis for specific delivery
     * Fixed to properly handle JSON deserialization and consistent response structure
     */
    public ImageAnalysisResponse getImageAnalysis(String deliveryId) {
        DeliveryRequest delivery = getDeliveryById(deliveryId);

        if (!delivery.isImageAnalyzed()) {
            throw new IllegalStateException("Image has not been analyzed for this delivery");
        }

        try {
            // If we have stored JSON analysis, deserialize it
            if (delivery.getPackageImageAnalysis() != null && !delivery.getPackageImageAnalysis().trim().isEmpty()) {
                try {
                    ImageAnalysisResponse storedAnalysis = objectMapper.readValue(
                            delivery.getPackageImageAnalysis(),
                            ImageAnalysisResponse.class
                    );

                    // Ensure the stored analysis has all required fields
                    if (storedAnalysis.getAnalysis() != null &&
                            storedAnalysis.getDeliveryRelevantInfo() != null) {
                        logger.info("Returning stored analysis for delivery: {}", deliveryId);
                        return storedAnalysis;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to deserialize stored analysis for delivery {}: {}",
                            deliveryId, e.getMessage());
                    // Fall through to manual construction
                }
            }

            // Fallback: manually construct response from individual fields
            logger.info("Manually constructing analysis response for delivery: {}", deliveryId);

            ImageAnalysisResponse response = new ImageAnalysisResponse();
            response.setSuccess(true);
            response.setError(null);

            // Create analysis object with text extraction
            ImageAnalysisResponse.Analysis analysis = new ImageAnalysisResponse.Analysis();
            ImageAnalysisResponse.Analysis.TextExtraction textExtraction =
                    new ImageAnalysisResponse.Analysis.TextExtraction();

            // Use camelCase for consistency with Angular expectations
            String extractedText = delivery.getExtractedText();
            textExtraction.setFullText(extractedText != null ? extractedText : "");

            analysis.setTextExtraction(textExtraction);

            // Create delivery relevant info
            ImageAnalysisResponse.DeliveryRelevantInfo deliveryInfo =
                    new ImageAnalysisResponse.DeliveryRelevantInfo();

            deliveryInfo.setImageQuality(delivery.getImageQuality() != null ?
                    delivery.getImageQuality() : "UNKNOWN");
            deliveryInfo.setHasText(extractedText != null && !extractedText.trim().isEmpty());
            deliveryInfo.setAnalyzedAt(delivery.getImageAnalyzedAt() != null ?
                    delivery.getImageAnalyzedAt().toString() : null);

            // Set the constructed objects
            response.setAnalysis(analysis);
            response.setDeliveryRelevantInfo(deliveryInfo);

            return response;

        } catch (Exception e) {
            logger.error("Error retrieving image analysis for delivery {}: {}", deliveryId, e.getMessage());

            // Return error response
            ImageAnalysisResponse errorResponse = new ImageAnalysisResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError("Failed to retrieve image analysis: " + e.getMessage());

            return errorResponse;
        }
    }

    /**
     * Helper method to validate and potentially re-save analysis data
     * Call this if you want to ensure all existing deliveries have properly formatted analysis
     */
    public void validateAndFixAnalysisData(String deliveryId) {
        DeliveryRequest delivery = getDeliveryById(deliveryId);

        if (!delivery.isImageAnalyzed()) {
            return;
        }

        // Check if the stored JSON is valid
        if (delivery.getPackageImageAnalysis() == null ||
                delivery.getPackageImageAnalysis().trim().isEmpty()) {

            // Reconstruct from individual fields
            try {
                ImageAnalysisResponse reconstructed = new ImageAnalysisResponse();
                reconstructed.setSuccess(true);

                ImageAnalysisResponse.Analysis analysis = new ImageAnalysisResponse.Analysis();
                ImageAnalysisResponse.Analysis.TextExtraction textExtraction =
                        new ImageAnalysisResponse.Analysis.TextExtraction();

                textExtraction.setFullText(delivery.getExtractedText());


                analysis.setTextExtraction(textExtraction);

                ImageAnalysisResponse.DeliveryRelevantInfo deliveryInfo =
                        new ImageAnalysisResponse.DeliveryRelevantInfo();
                deliveryInfo.setImageQuality(delivery.getImageQuality());
                boolean hasText = delivery.getExtractedText() != null &&
                        !delivery.getExtractedText().trim().isEmpty();
                deliveryInfo.setHasText(hasText);
                deliveryInfo.setHasText(delivery.getExtractedText() != null &&
                        !delivery.getExtractedText().trim().isEmpty());

                reconstructed.setAnalysis(analysis);
                reconstructed.setDeliveryRelevantInfo(deliveryInfo);

                // Save the reconstructed analysis
                delivery.setPackageImageAnalysis(objectMapper.writeValueAsString(reconstructed));
                deliveryRepository.save(delivery);

                logger.info("Reconstructed and saved analysis data for delivery: {}", deliveryId);

            } catch (Exception e) {
                logger.error("Failed to reconstruct analysis data for delivery {}: {}",
                        deliveryId, e.getMessage());
            }
        }
    }


    @Override
    public DeliveryRequest createDelivery(DeliveryRequest delivery) {
        double calculatedPrice = pricingService.calculatePrice(delivery);
        delivery.setAmount(calculatedPrice);
        delivery.setFinalAmountAfterDiscount(calculatedPrice);

        // Set created timestamp
        if (delivery.getCreatedAt() == null) {
            delivery.setCreatedAt(LocalDateTime.now());
        }

        // Ensure proper status for new delivery
        if (delivery.getStatus() == null) {
            delivery.setStatus(DeliveryRequest.DeliveryReqStatus.PENDING);
        }

        // Save the delivery first
        logger.info("Creating new delivery request from client ID: {}", delivery.getClientId());
        DeliveryRequest savedDelivery = deliveryRepository.save(delivery);
        logger.info("Successfully created delivery with ID: {}", savedDelivery.getId());

        // Only update vehicle availability if vehicleId is provided
        if (savedDelivery.getVehicleId() != null && !savedDelivery.getVehicleId().isEmpty()) {
            try {
                vehicleService.setVehicleUnavailable(savedDelivery.getVehicleId());
                logger.info("Marked vehicle {} as unavailable", savedDelivery.getVehicleId());
            } catch (Exception e) {
                logger.error("Failed to update vehicle availability: {}", e.getMessage());
                // Continue execution - this shouldn't prevent delivery assignment
            }
        }

        // Attempt immediate assignment only if no delivery person is already specified
        if (savedDelivery.getDeliveryPersonId() == null || savedDelivery.getDeliveryPersonId().isEmpty()) {
            logger.info("Attempting immediate assignment for delivery: {}", savedDelivery.getId());
            try {
                DeliveryRequest assigned = deliveryAssignmentService.assignDelivery(savedDelivery.getId());
                if (assigned.getDeliveryPersonId() != null && !assigned.getDeliveryPersonId().isEmpty()) {
                    logger.info("Delivery {} successfully assigned to delivery person: {}",
                            assigned.getId(), assigned.getDeliveryPersonId());
                    return assigned; // Return the updated delivery with assignment info
                } else {
                    logger.warn("Immediate assignment failed - no delivery person was assigned");
                }
            } catch (Exception e) {
                logger.error("Error during immediate assignment: {}", e.getMessage(), e);
                logger.info("Delivery {} will be picked up by scheduled assignment.",
                        savedDelivery.getId());
            }
        } else {
            logger.info("Delivery {} already has a delivery person assigned: {}. Skipping assignment.",
                    savedDelivery.getId(), savedDelivery.getDeliveryPersonId());
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

        // Return the saved delivery (whether assignment worked or not)
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
    public DeliveryRequest updateDeliveryPaymentStatus(String deliveryId, String paymentId, PaymentStatus paymentStatus) {
        DeliveryRequest delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + deliveryId));

        // Update payment-related fields
        delivery.setPaymentId(paymentId);
        delivery.setPaymentStatus(paymentStatus);
        delivery.setUpdatedAt(LocalDateTime.now());

        return deliveryRepository.save(delivery);
    }

    @Override
    public List<DeliveryRequest> getPendingDeliveries() {
        return deliveryRepository.findByStatus(DeliveryRequest.DeliveryReqStatus.PENDING);
    }

    @Override
    public DeliveryRequest updateDeliveryStatus(String id, DeliveryRequest.DeliveryReqStatus status) {
        DeliveryRequest delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));

        DeliveryRequest.DeliveryReqStatus previousStatus = delivery.getStatus();
        delivery.setStatus(status);

        // Handle status-specific updates and mission auto-completion
        switch (status) {
            case IN_TRANSIT:
                delivery.setStartedAt(LocalDateTime.now());
                // Auto-complete mission when delivery status changes to IN_TRANSIT
                autoCompleteMissionOnInTransit(id, delivery.getDeliveryPersonId());
                break;
            case DELIVERED:
                delivery.setCompletedAt(LocalDateTime.now());
                // Update delivery person stats if delivery person exists
                if (delivery.getDeliveryPersonId() != null) {
                    try {
                        User deliveryPerson = userService.findById(delivery.getDeliveryPersonId());
                        deliveryPerson.updateRating(5.0);
                        deliveryPerson.setTotalDeliveries(deliveryPerson.getTotalDeliveries() + 1);
                        userService.save(deliveryPerson);
                    } catch (Exception e) {
                        logger.error("Failed to update delivery person stats: {}", e.getMessage());
                    }
                }
                // Complete mission on delivery completion
                autoCompleteMissionOnDelivered(id, delivery.getDeliveryPersonId());
                break;
            case CANCELLED:
                // Cancel related mission if exists
                autoCancelMissionOnDeliveryCancelled(id, delivery.getDeliveryPersonId());
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

    /**
     * Auto-cancel mission when delivery is cancelled
     */
    private void autoCancelMissionOnDeliveryCancelled(String deliveryId, String deliveryPersonId) {
        try {
            Optional<Mission> missionOpt = missionRepository.findByDeliveryRequestId(deliveryId);

            if (missionOpt.isPresent()) {
                Mission mission = missionOpt.get();

                // Cancel mission if not already completed or cancelled
                if (!"COMPLETED".equals(mission.getStatus()) && !"CANCELLED".equals(mission.getStatus())) {
                    logger.info("Auto-cancelling mission {} for delivery {} due to delivery cancellation",
                            mission.getId(), deliveryId);

                    mission.setStatus("CANCELLED");
                    mission.setEndTime(LocalDateTime.now());
                    missionRepository.save(mission);

                    logger.info("Mission {} cancelled due to delivery cancellation", mission.getId());
                }
            }
        } catch (Exception e) {
            logger.error("Error cancelling mission for cancelled delivery {}: {}", deliveryId, e.getMessage(), e);
        }
    }

    @Override
    public void cancelDelivery(String id) {
        // This version can be used by admin or system without client verification
        DeliveryRequest delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));

        delivery.setStatus(DeliveryRequest.DeliveryReqStatus.CANCELLED);
        DeliveryRequest savedDelivery = deliveryRepository.save(delivery);

        // Auto-cancel related mission
        autoCancelMissionOnDeliveryCancelled(id, delivery.getDeliveryPersonId());
    }

    @Override
    public void cancelDelivery(String id, String clientId) {
        DeliveryRequest delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));

        if (!delivery.getClientId().equals(clientId)) {
            throw new IllegalStateException("Not authorized to cancel this delivery");
        }

        delivery.setStatus(DeliveryRequest.DeliveryReqStatus.CANCELLED);
        DeliveryRequest savedDelivery = deliveryRepository.save(delivery);

        // Auto-cancel related mission
        autoCancelMissionOnDeliveryCancelled(id, delivery.getDeliveryPersonId());
    }

    @Scheduled(cron = "0 0 0 * * ?") // Runs daily at midnight
    public void expireOldDeliveries() {
        try {
            LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);

            // First: count eligible deliveries for expiration
            long eligibleCount = deliveryRepository.countPendingDeliveriesOlderThan(threeDaysAgo);
            logger.info("Found {} deliveries eligible for expiration", eligibleCount);

            if (eligibleCount > 0) {
                // Second: execute update operation
                deliveryRepository.expirePendingDeliveriesOlderThan(
                        threeDaysAgo,
                        DeliveryRequest.DeliveryReqStatus.EXPIRED
                );

                logger.info("Successfully expired {} pending deliveries older than {}", eligibleCount, threeDaysAgo);
            } else {
                logger.info("No deliveries found to expire");
            }
        } catch (Exception e) {
            logger.error("Failed to expire old deliveries", e);
            throw new RuntimeException("Failed to expire deliveries", e);
        }
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

        // Reset assignment fields and status
        delivery.setDeliveryPersonId(null);
        delivery.setAssignedAt(null);
        delivery.setStatus(DeliveryRequest.DeliveryReqStatus.PENDING);

        DeliveryRequest savedDelivery = deliveryRepository.save(delivery);

        // Cancel related mission if exists
        autoCancelMissionOnDeliveryCancelled(deliveryId, deliveryPersonId);

        return savedDelivery;
    }

    @Override
    public List<DeliveryResponseDTO> getAssignedPendingDeliveries(String deliveryPersonId) {
        logger.info("Fetching assigned pending deliveries for user: {}", deliveryPersonId);

        List<DeliveryRequest> deliveries = deliveryRepository.findByStatusInAndDeliveryPersonId(
                Arrays.asList(DeliveryRequest.DeliveryReqStatus.PENDING, DeliveryRequest.DeliveryReqStatus.ASSIGNED),
                deliveryPersonId
        );

        logger.info("Found {} deliveries with statuses PENDING or ASSIGNED", deliveries.size());
        return deliveries.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private DeliveryResponseDTO convertToDto(DeliveryRequest delivery) {
        // Convert all LocalDateTime fields to Date with proper timezone handling
        Date scheduledDate = delivery.getScheduledDate() != null ?
                Date.from(delivery.getScheduledDate().atZone(ZoneId.systemDefault()).toInstant()) : null;
        Date createdAt = delivery.getCreatedAt() != null ?
                Date.from(delivery.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()) : null;
        Date updatedAt = delivery.getUpdatedAt() != null ?
                Date.from(delivery.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()) : null;
        Date assignedAt = delivery.getAssignedAt() != null ?
                Date.from(delivery.getAssignedAt().atZone(ZoneId.systemDefault()).toInstant()) : null;
        Date startedAt = delivery.getStartedAt() != null ?
                Date.from(delivery.getStartedAt().atZone(ZoneId.systemDefault()).toInstant()) : null;
        Date completedAt = delivery.getCompletedAt() != null ?
                Date.from(delivery.getCompletedAt().atZone(ZoneId.systemDefault()).toInstant()) : null;

        UserDTO deliveryPersonDto = null;
        if (delivery.getDeliveryPersonId() != null) {
            User deliveryPerson = userService.findById(delivery.getDeliveryPersonId());
            deliveryPersonDto = userService.convertToDTO(deliveryPerson);
        }

        return new DeliveryResponseDTO(
                delivery.getId(),
                delivery.getPickupAddress(),
                delivery.getDeliveryAddress(),
                delivery.getPackageDescription(),
                delivery.getPackageWeight(),
                delivery.getVehicleId(),
                scheduledDate,
                delivery.getAdditionalInstructions(),
                delivery.getStatus().name(),
                createdAt,
                delivery.getClientId(),
                updatedAt,
                assignedAt,
                startedAt,
                completedAt,
                delivery.getPickupLatitude(),
                delivery.getPickupLongitude(),
                delivery.getDeliveryLatitude(),
                delivery.getDeliveryLongitude(),
                delivery.getRating(),
                delivery.isRated(),
                null,   // deliveryPerson (to be set in getDeliveryWithDetails)
                null    // assignedVehicle (to be set in getDeliveryWithDetails)
        );
    }

    @Override
    public List<DeliveryResponseDTO> getDeliveryHistory(String deliveryPersonId) {
        logger.info("Fetching delivery history for user: {}", deliveryPersonId);

        // Sort by updatedAt in descending order (newest first)
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt");
        List<DeliveryRequest> deliveries = deliveryRepository.findDeliveryHistoryByDeliveryPerson(deliveryPersonId, sort);

        logger.info("Found {} historical deliveries", deliveries.size());
        return deliveries.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    public void rateDelivery(String deliveryId, double rating, String clientId) {
        DeliveryRequest delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found"));

        if (!clientId.equals(delivery.getClientId())) {
            throw new IllegalArgumentException("Not authorized to rate this delivery");
        }

        if (delivery.getStatus() != DeliveryRequest.DeliveryReqStatus.DELIVERED) {
            throw new IllegalStateException("Delivery not completed yet");
        }

        if (delivery.isRated()) {
            throw new IllegalStateException("Delivery already rated");
        }

        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        // Update delivery
        delivery.setRating(rating);
        delivery.setRated(true);
        delivery.setStatus(DeliveryRequest.DeliveryReqStatus.RATED);
        deliveryRepository.save(delivery);

        // Update delivery person
        if (delivery.getDeliveryPersonId() != null) {
            User deliveryPerson = userService.findById(delivery.getDeliveryPersonId());
            deliveryPerson.updateRating(rating);
            userService.save(deliveryPerson);
        }
    }

    @Override
    public DeliveryResponseDTO getDeliveryWithDetails(String deliveryId) {
        DeliveryRequest delivery = getDeliveryById(deliveryId);
        DeliveryResponseDTO baseDto = convertToDto(delivery);

        UserDTO deliveryPersonDto = null;
        VehicleDTO vehicleDto = null;

        if (delivery.getDeliveryPersonId() != null) {
            User deliveryPerson = userService.findById(delivery.getDeliveryPersonId());
            deliveryPersonDto = userService.convertToDTO(deliveryPerson);

            if (deliveryPerson.getAssignedVehicle() != null) {
                vehicleDto = convertVehicleToDTO(deliveryPerson.getAssignedVehicle());
            }
        }

        // Create a new DTO with updated values
        return new DeliveryResponseDTO(
                baseDto.id(),
                baseDto.pickupAddress(),
                baseDto.deliveryAddress(),
                baseDto.packageDescription(),
                baseDto.packageWeight(),
                baseDto.vehicleId(),
                baseDto.scheduledDate(),
                baseDto.additionalInstructions(),
                baseDto.status(),
                baseDto.createdAt(),
                baseDto.clientId(),
                baseDto.updatedAt(),
                baseDto.assignedAt(),
                baseDto.startedAt(),
                baseDto.completedAt(),
                baseDto.pickupLatitude(),
                baseDto.pickupLongitude(),
                baseDto.deliveryLatitude(),
                baseDto.deliveryLongitude(),
                baseDto.rating(),
                baseDto.rated(),
                deliveryPersonDto,
                vehicleDto
        );
    }

    private VehicleDTO convertVehicleToDTO(Vehicle vehicle) {
        if (vehicle == null) return null;

        VehicleDTO dto = new VehicleDTO();
        dto.setId(vehicle.getId());
        dto.setVehicleType(vehicle.getVehicleType());
        dto.setVehicleBrand(vehicle.getMake());
        dto.setVehicleModel(vehicle.getModel());
        dto.setVehicleYear(vehicle.getYear());
        dto.setVehiclePlateNumber(vehicle.getLicensePlate());
        dto.setVehicleCapacityKg(vehicle.getMaxLoad());
        dto.setVehiclePhotoUrl(vehicle.getPhotoPath());
        dto.setAvailable(vehicle.isAvailable());

        // Set default values for unused fields
        dto.setVehicleColor("N/A");
        dto.setVehicleVolumeM3(0.0);
        dto.setVehicleHasFridge(false);
        dto.setVehicleInsuranceExpiry(new Date());
        dto.setVehicleInspectionExpiry(new Date());

        return dto;
    }

    @Override
    public int countTodayCompletedDeliveries(String deliveryPersonId) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

        return deliveryRepository.countByDeliveryPersonIdAndStatusAndCompletedAtBetween(
                deliveryPersonId,
                DeliveryRequest.DeliveryReqStatus.DELIVERED,
                startOfDay,
                endOfDay
        );
    }
}