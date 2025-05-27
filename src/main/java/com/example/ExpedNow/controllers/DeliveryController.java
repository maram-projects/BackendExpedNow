package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.DeliveryRequestDTO;
import com.example.ExpedNow.dto.DeliveryResponseDTO;
import com.example.ExpedNow.dto.StatusUpdateRequest;
import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.models.Mission;
import com.example.ExpedNow.models.enums.PackageType;
import com.example.ExpedNow.security.CustomUserDetailsService;
import com.example.ExpedNow.services.core.MissionServiceInterface;
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

    // التصحيح: حقن MissionService في الكونستركتور
    public DeliveryController(DeliveryServiceImpl deliveryService,
                              MissionServiceInterface missionService) {
        this.deliveryService = deliveryService;
        this.missionService = missionService;
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
                delivery.getDeliveryLongitude()
        );
    }

}