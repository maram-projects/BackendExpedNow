package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.*;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.models.Mission;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.enums.PackageType;
import com.example.ExpedNow.models.enums.PaymentMethod;
import com.example.ExpedNow.models.enums.PaymentStatus;
import com.example.ExpedNow.security.CustomUserDetailsService;
import com.example.ExpedNow.services.core.MissionServiceInterface;
import com.example.ExpedNow.services.core.UserServiceInterface;
import com.example.ExpedNow.services.core.impl.DeliveryServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryController.class);
    private final DeliveryServiceImpl deliveryService;
    private final MissionServiceInterface missionService;
    private final UserServiceInterface userService;
    // التصحيح: حقن MissionService في الكونستركتور
    public DeliveryController(DeliveryServiceImpl deliveryService,
                              MissionServiceInterface missionService, UserServiceInterface userService) {
        this.deliveryService = deliveryService;
        this.missionService = missionService;
        this.userService = userService;
    }

    @GetMapping("/{deliveryId}/with-assigned")
    public ResponseEntity<Map<String, Object>> getDeliveryWithAssignedPerson(@PathVariable String deliveryId) {
        try {
            DeliveryRequest delivery = deliveryService.getDeliveryById(deliveryId);
            Map<String, Object> response = new HashMap<>();
            response.put("delivery", convertToDto(delivery));

            if (delivery.getDeliveryPersonId() != null) {
                User deliveryPerson = userService.findById(delivery.getDeliveryPersonId());

                Map<String, Object> personInfo = new HashMap<>();
                personInfo.put("id", deliveryPerson.getId());
                // Ensure these fields aren't null
                personInfo.put("firstName", deliveryPerson.getFirstName() != null ? deliveryPerson.getFirstName() : "");
                personInfo.put("lastName", deliveryPerson.getLastName() != null ? deliveryPerson.getLastName() : "");
                personInfo.put("fullName", deliveryPerson.getFullName()); // Add getFullName() method if needed
                personInfo.put("phone", deliveryPerson.getPhone() != null ? deliveryPerson.getPhone() : "");
                personInfo.put("email", deliveryPerson.getEmail());

                // Add rating information
                personInfo.put("rating", deliveryPerson.getRating());
                personInfo.put("ratingCount", deliveryPerson.getRatingCount());
                personInfo.put("completedDeliveries", deliveryPerson.getCompletedDeliveries());

                // Vehicle information
                if (deliveryPerson.getAssignedVehicle() != null) {
                    Map<String, Object> vehicleInfo = new HashMap<>();
                    vehicleInfo.put("model", deliveryPerson.getAssignedVehicle().getModel());
                    vehicleInfo.put("licensePlate", deliveryPerson.getAssignedVehicle().getLicensePlate());
                    vehicleInfo.put("type", deliveryPerson.getAssignedVehicle().getVehicleType());
                    personInfo.put("vehicle", vehicleInfo);
                }

                response.put("assignedDeliveryPerson", personInfo);
            }

            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{deliveryId}/payment-status")
    public ResponseEntity<Map<String, Object>> updateDeliveryPaymentStatus(
            @PathVariable String deliveryId,
            @RequestBody Map<String, String> paymentUpdate) {

        try {
            // Validate input
            if (!paymentUpdate.containsKey("paymentId") || !paymentUpdate.containsKey("paymentStatus")) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Missing required fields: paymentId or paymentStatus");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Parse payment status
            PaymentStatus paymentStatus;
            try {
                paymentStatus = PaymentStatus.valueOf(paymentUpdate.get("paymentStatus"));
            } catch (IllegalArgumentException e) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Invalid payment status: " + paymentUpdate.get("paymentStatus"));
                return ResponseEntity.badRequest().body(errorResponse);
            }

            DeliveryRequest delivery = deliveryService.updateDeliveryPaymentStatus(
                    deliveryId,
                    paymentUpdate.get("paymentId"),
                    paymentStatus
            );

            // Also update payment method if provided
            if (paymentUpdate.containsKey("paymentMethod")) {
                try {
                    PaymentMethod paymentMethod = PaymentMethod.valueOf(paymentUpdate.get("paymentMethod"));
                    delivery.setPreferredPaymentMethod(paymentMethod);
                } catch (IllegalArgumentException e) {
                    // Log but don't fail the request
                    logger.warn("Invalid payment method provided: " + paymentUpdate.get("paymentMethod"));
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", delivery);
            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        } catch (Exception e) {
            logger.error("Error updating delivery payment status", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Internal server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    @PostMapping("/request")
    @PreAuthorize("hasRole('CLIENT') or hasRole('INDIVIDUAL') or hasRole('ENTERPRISE')")
    public ResponseEntity<?> createDeliveryRequest(
            @Valid @RequestBody DeliveryRequestDTO requestDTO,
            @RequestParam(required = false) String clientId) {

        try {
            DeliveryRequest delivery = new DeliveryRequest();
            delivery.setPickupAddress(requestDTO.pickupAddress());
            delivery.setPickupAddress(requestDTO.pickupAddress());
            delivery.setDeliveryAddress(requestDTO.deliveryAddress());
            delivery.setPackageDescription(requestDTO.packageDescription());
            delivery.setPackageWeight(requestDTO.packageWeight());
            delivery.setClientId(requestDTO.clientId());  // Get from DTO


            // Handle package type safely
            if (requestDTO.packageType() != null && !requestDTO.packageType().isEmpty()) {
                try {
                    PackageType packageType = PackageType.valueOf(requestDTO.packageType());
                    delivery.setPackageType(packageType);
                } catch (IllegalArgumentException e) {
                    // Log the exception but don't fail (default will be used)
                    System.out.println("Invalid package type: " + requestDTO.packageType());
                }
            }

            // Optional vehicle ID
            if (requestDTO.vehicleId() != null && !requestDTO.vehicleId().isEmpty()) {
                delivery.setVehicleId(requestDTO.vehicleId());
            }

            Date scheduledDateFromDTO = requestDTO.scheduledDate();
            if (scheduledDateFromDTO != null) {
                delivery.setScheduledDate(scheduledDateFromDTO.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime());
            } else {
                delivery.setScheduledDate(null);
            }

            delivery.setAdditionalInstructions(requestDTO.additionalInstructions());

            // Set coordinates if present
            if (requestDTO.pickupLatitude() != null) {
                delivery.setPickupLatitude(requestDTO.pickupLatitude());
            }
            if (requestDTO.pickupLongitude() != null) {
                delivery.setPickupLongitude(requestDTO.pickupLongitude());
            }
            if (requestDTO.deliveryLatitude() != null) {
                delivery.setDeliveryLatitude(requestDTO.deliveryLatitude());
            }
            if (requestDTO.deliveryLongitude() != null) {
                delivery.setDeliveryLongitude(requestDTO.deliveryLongitude());
            }

            delivery.setClientId(clientId);
            delivery.setStatus(DeliveryRequest.DeliveryReqStatus.PENDING);
            delivery.setCreatedAt(LocalDateTime.now());

            DeliveryRequest savedDelivery = deliveryService.createDelivery(delivery);
            return new ResponseEntity<>(convertToDto(savedDelivery), HttpStatus.CREATED);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error creating delivery: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // للإدارة
    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> adminCancelDelivery(@PathVariable String id) {
        deliveryService.cancelDelivery(id);
        return ResponseEntity.noContent().build();
    }

    // للعملاء
    @DeleteMapping("/client/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Void> clientCancelDelivery(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String clientId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();
        deliveryService.cancelDelivery(id, clientId);
        return ResponseEntity.noContent().build();
    }

    // في DeliveryController.java
    @PostMapping("/expire-old")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> expireOldDeliveries() {
        deliveryService.expireOldDeliveries();
        return ResponseEntity.ok().build();
    }
    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('DELIVERY_PERSON')")
    @Transactional
    public ResponseEntity<?> acceptDelivery(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {

        String deliveryPersonId = request.get("deliveryPersonId");

        try {
            logger.info("Accepting delivery {} by user {}", id, deliveryPersonId);

            // 1. تحقق من الطلب
            DeliveryRequest delivery = deliveryService.getDeliveryById(id);

            // 2. تحقق من أن الطلب معين للموصل المحدد
            if (!deliveryPersonId.equals(delivery.getDeliveryPersonId())) {
                logger.warn("Unauthorized access attempt by user {}", deliveryPersonId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Not authorized to accept this delivery");
            }

            // 3. تحقق من الحالة (إما PENDING أو ASSIGNED)
            if (delivery.getStatus() != DeliveryRequest.DeliveryReqStatus.ASSIGNED &&
                    delivery.getStatus() != DeliveryRequest.DeliveryReqStatus.PENDING) {
                return ResponseEntity.badRequest()
                        .body("Cannot accept delivery with status: " + delivery.getStatus());
            }

            // 4. إنشاء مهمة (ستقوم بتحديث الحالة تلقائيًا إلى APPROVED)
            Mission newMission = missionService.createMission(id, deliveryPersonId);
            logger.info("New mission created with ID: {}", newMission.getId());

            // تحديث حالة الطلب بشكل صريح (للتأكد)
            delivery = deliveryService.updateDeliveryStatus(id, DeliveryRequest.DeliveryReqStatus.APPROVED);

            // 5. تحضير الرد
            Map<String, Object> response = new HashMap<>();
            response.put("delivery", convertToDto(delivery));
            response.put("mission", newMission);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error in acceptDelivery:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error accepting delivery: " + e.getMessage());
        }
    }
    @GetMapping("/assigned-pending")
    @PreAuthorize("hasRole('DELIVERY_PERSON')")

    public ResponseEntity<List<DeliveryResponseDTO>> getAssignedPendingDeliveries(
            @AuthenticationPrincipal UserDetails userDetails) {

        String deliveryPersonId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();
        logger.info("User ID: {}", deliveryPersonId);

        // هنا عدلنا نوع المتغير ليكون List<DeliveryResponseDTO>
        List<DeliveryResponseDTO> deliveries = deliveryService.getAssignedPendingDeliveries(deliveryPersonId);
        logger.info("Raw deliveries from DB: {}", deliveries);

        return ResponseEntity.ok(deliveries); // لا حاجة لـ map() لأن البيانات جاهزة
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('DELIVERY_PERSON')")
    public ResponseEntity<DeliveryResponseDTO> rejectDelivery(
            @PathVariable String id,
            @RequestParam String deliveryPersonId) {

        DeliveryRequest updated = deliveryService.resetDeliveryAssignment(id, deliveryPersonId);
        return ResponseEntity.ok(convertToDto(updated));
    }

    @GetMapping
    @PreAuthorize("hasRole('CLIENT') or hasRole('INDIVIDUAL') or hasRole('ENTERPRISE') or hasRole('ADMIN')")
    public ResponseEntity<List<DeliveryResponseDTO>> getDeliveries(
            @RequestParam(required = false) String clientId) {

        List<DeliveryRequest> deliveries;

        if (clientId != null && !clientId.isEmpty()) {
            deliveries = deliveryService.getClientDeliveries(clientId);
        } else {
            // Only allow admins to view all deliveries
            deliveries = deliveryService.getAllDeliveries();
        }

        List<DeliveryResponseDTO> responseDTOs = deliveries.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDTOs);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('DELIVERY_PERSON') or hasRole('ADMIN')")
    public ResponseEntity<List<DeliveryResponseDTO>> getPendingDeliveries() {

        List<DeliveryRequest> pendingDeliveries = deliveryService.getPendingDeliveries();

        List<DeliveryResponseDTO> responseDTOs = pendingDeliveries.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDTOs);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable String id,
            @RequestBody StatusUpdateRequest statusRequest) {

        try {
            DeliveryRequest.DeliveryReqStatus newStatus =
                    DeliveryRequest.DeliveryReqStatus.valueOf(statusRequest.status().toUpperCase());

            DeliveryRequest updatedDelivery = deliveryService.updateDeliveryStatus(id, newStatus);
            return ResponseEntity.ok(convertToDto(updatedDelivery));

        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid status value");
            errorResponse.put("validStatuses", Arrays.toString(DeliveryRequest.DeliveryReqStatus.values()));
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    @GetMapping("/history")
    @PreAuthorize("hasRole('DELIVERY_PERSON')")
    public ResponseEntity<List<DeliveryResponseDTO>> getDeliveryHistory(
            @AuthenticationPrincipal UserDetails userDetails) {

        String deliveryPersonId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();
        logger.info("Fetching delivery history for user: {}", deliveryPersonId);

        List<DeliveryResponseDTO> history = deliveryService.getDeliveryHistory(deliveryPersonId);
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CLIENT') or hasRole('INDIVIDUAL') or hasRole('ENTERPRISE') or hasRole('ADMIN')")
    public ResponseEntity<Void> cancelDelivery(@PathVariable String id) {
        deliveryService.cancelDelivery(id);
        return ResponseEntity.noContent().build();
    }

    // Helper method to map Entity to DTO
    private DeliveryResponseDTO convertToDto(DeliveryRequest delivery) {
        // Convert LocalDateTime to Date with proper timezone handling
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
                null    // assignedVehicle (to be set in getDeliveryWithDetails
        );
    }
    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryRequest> getDeliveryById(@PathVariable String deliveryId) {
        DeliveryRequest delivery = deliveryService.getDeliveryById(deliveryId);
        return ResponseEntity.ok(delivery);
    }

    @PostMapping("/{deliveryId}/rate")
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENT','ROLE_INDIVIDUAL','ROLE_ENTERPRISE')")
    public ResponseEntity<?> rateDelivery(
            @PathVariable String deliveryId,
            @Valid @RequestBody RatingRequest ratingRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String clientId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();

            // Get the delivery
            DeliveryRequest delivery = deliveryService.getDeliveryById(deliveryId);

            // Validate delivery status
            if (delivery.getStatus() != DeliveryRequest.DeliveryReqStatus.DELIVERED) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Delivery not completed yet"));
            }

            // Validate client is the owner
            if (!delivery.getClientId().equals(clientId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Not authorized to rate this delivery"));
            }

            // Validate not already rated
            if (delivery.isRated()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Delivery already rated"));
            }

            // Process the rating
            deliveryService.rateDelivery(deliveryId, ratingRequest.rating(), clientId);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/with-details")
    public DeliveryResponseDTO getDeliveryWithDetails(@PathVariable String id) {
        return deliveryService.getDeliveryWithDetails(id);
    }


    // In your DeliveryController.java
    @GetMapping("/today-completed")
    @PreAuthorize("hasRole('DELIVERY_PERSON')")
    public ResponseEntity<Integer> getTodayCompletedDeliveries(
            @AuthenticationPrincipal UserDetails userDetails) {

        String deliveryPersonId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();
        int count = deliveryService.countTodayCompletedDeliveries(deliveryPersonId);
        return ResponseEntity.ok(count);
    }
}