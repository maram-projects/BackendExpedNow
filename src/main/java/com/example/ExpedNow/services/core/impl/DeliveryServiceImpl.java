    package com.example.ExpedNow.services.core.impl;

    import com.example.ExpedNow.dto.DeliveryResponseDTO;
    import com.example.ExpedNow.dto.UserDTO;
    import com.example.ExpedNow.dto.VehicleDTO;
    import com.example.ExpedNow.models.DeliveryRequest;
    import com.example.ExpedNow.models.User;
    import com.example.ExpedNow.models.Vehicle;
    import com.example.ExpedNow.models.enums.PaymentStatus;
    import com.example.ExpedNow.repositories.DeliveryReqRepository;
    import com.example.ExpedNow.services.core.DeliveryPricingService;
    import com.example.ExpedNow.services.core.DeliveryServiceInterface;
    import com.example.ExpedNow.services.core.UserServiceInterface;
    import com.example.ExpedNow.services.core.VehicleServiceInterface;
    import com.example.ExpedNow.exception.ResourceNotFoundException;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.context.annotation.Primary;
    import org.springframework.data.domain.Sort;
    import org.springframework.scheduling.annotation.Scheduled;
    import org.springframework.stereotype.Service;

    import java.time.LocalDateTime;
    import java.time.ZoneId;
    import java.util.Arrays;
    import java.util.Date;
    import java.util.List;
    import java.util.stream.Collectors;

    @Service
    @Primary
    public class DeliveryServiceImpl implements DeliveryServiceInterface {
        private static final Logger logger = LoggerFactory.getLogger(DeliveryServiceImpl.class);
        private final UserServiceInterface userService; // أضف هذا

        private final DeliveryReqRepository deliveryRepository;
        private final VehicleServiceInterface vehicleService;
        @Autowired
        private DeliveryPricingService pricingService;
        @Autowired
        private DeliveryAssignmentServiceImpl deliveryAssignmentService;

        public DeliveryServiceImpl(UserServiceInterface userService, DeliveryReqRepository deliveryRepository, VehicleServiceInterface vehicleService) {
            this.userService = userService;
            this.deliveryRepository = deliveryRepository;
            this.vehicleService = vehicleService;
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
            delivery.setStatus(status);
            return deliveryRepository.save(delivery);
        }

        /**
         * @param id
         */



        /**
         * @param id
         */
        // في DeliveryServiceImpl
        @Override
        public void cancelDelivery(String id) {
            // هذا الإصدار يمكن استخدامه من قبل الأدمن أو النظام بدون التحقق من العميل
            DeliveryRequest delivery = deliveryRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));

            delivery.setStatus(DeliveryRequest.DeliveryReqStatus.CANCELLED);
            deliveryRepository.save(delivery);
        }

        @Override
        public void cancelDelivery(String id, String clientId) {
            DeliveryRequest delivery = deliveryRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));

            if (!delivery.getClientId().equals(clientId)) {
                throw new IllegalStateException("Not authorized to cancel this delivery");
            }

            delivery.setStatus(DeliveryRequest.DeliveryReqStatus.CANCELLED);
            deliveryRepository.save(delivery);
        }



        // في DeliveryServiceImpl.java
        @Scheduled(cron = "0 0 0 * * ?") // يعمل يومياً عند منتصف الليل
        public void expireOldDeliveries() {
            try {
                LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);

                // أولاً: عد الطلبات المؤهلة للانتهاء
                long eligibleCount = deliveryRepository.countPendingDeliveriesOlderThan(threeDaysAgo);
                logger.info("Found {} deliveries eligible for expiration", eligibleCount);

                if (eligibleCount > 0) {
                    // ثانياً: تنفيذ عملية التحديث
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
            delivery.setStatus(DeliveryRequest.DeliveryReqStatus.PENDING); // Add this line

            return deliveryRepository.save(delivery);
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

        // In DeliveryServiceImpl.java
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