package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.DeliveryRequestDTO;
import com.example.ExpedNow.dto.DeliveryResponseDTO;
import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.models.enums.PackageType;
import com.example.ExpedNow.security.CustomUserDetailsService;
import com.example.ExpedNow.services.core.impl.DeliveryServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryController.class);
    private final DeliveryServiceImpl deliveryService;

    public DeliveryController(DeliveryServiceImpl deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/request")
    @PreAuthorize("hasRole('CLIENT') or hasRole('INDIVIDUAL') or hasRole('ENTERPRISE')")
    public ResponseEntity<?> createDeliveryRequest(
            @Valid @RequestBody DeliveryRequestDTO requestDTO,
            @RequestParam(required = false) String clientId) {

        try {
            DeliveryRequest delivery = new DeliveryRequest();
            delivery.setPickupAddress(requestDTO.pickupAddress());
            delivery.setDeliveryAddress(requestDTO.deliveryAddress());
            delivery.setPackageDescription(requestDTO.packageDescription());
            delivery.setPackageWeight(requestDTO.packageWeight());

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

            delivery.setScheduledDate(requestDTO.scheduledDate());
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
            delivery.setCreatedAt(new Date());

            DeliveryRequest savedDelivery = deliveryService.createDelivery(delivery);
            return new ResponseEntity<>(mapToDTO(savedDelivery), HttpStatus.CREATED);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error creating delivery: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('DELIVERY_PERSON')")
    public ResponseEntity<DeliveryResponseDTO> acceptDelivery(
            @PathVariable String id,
            @RequestParam String deliveryPersonId) {

        DeliveryRequest delivery = deliveryService.getDeliveryById(id);

        // Verify assignment
        if (!deliveryPersonId.equals(delivery.getDeliveryPersonId())) {
            throw new IllegalStateException("Not authorized to accept this delivery");
        }

        DeliveryRequest updated = deliveryService.updateDeliveryStatus(
                id,
                DeliveryRequest.DeliveryReqStatus.APPROVED
        );

        return ResponseEntity.ok(mapToDTO(updated));
    }
    @GetMapping("/assigned-pending")
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
        return ResponseEntity.ok(mapToDTO(updated));
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
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDTOs);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('DELIVERY_PERSON') or hasRole('ADMIN')")
    public ResponseEntity<List<DeliveryResponseDTO>> getPendingDeliveries() {

        List<DeliveryRequest> pendingDeliveries = deliveryService.getPendingDeliveries();

        List<DeliveryResponseDTO> responseDTOs = pendingDeliveries.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDTOs);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('DELIVERY_PERSON') or hasRole('ADMIN')")
    public ResponseEntity<DeliveryResponseDTO> updateStatus(
            @PathVariable String id,
            @RequestBody StatusUpdateRequest statusRequest) {

        DeliveryRequest.DeliveryReqStatus newStatus = DeliveryRequest.DeliveryReqStatus.valueOf(statusRequest.status());
        DeliveryRequest updatedDelivery = deliveryService.updateDeliveryStatus(id, newStatus);

        return ResponseEntity.ok(mapToDTO(updatedDelivery));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CLIENT') or hasRole('INDIVIDUAL') or hasRole('ENTERPRISE') or hasRole('ADMIN')")
    public ResponseEntity<Void> cancelDelivery(@PathVariable String id) {
        deliveryService.cancelDelivery(id);
        return ResponseEntity.noContent().build();
    }


    // Helper method to map Entity to DTO
    private DeliveryResponseDTO mapToDTO(DeliveryRequest delivery) {
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

    // Inner record for status update requests
    private record StatusUpdateRequest(String status) {}
}