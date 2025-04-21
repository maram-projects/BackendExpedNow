package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.DeliveryRequestDTO;
import com.example.ExpedNow.dto.DeliveryResponseDTO;
import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.services.core.impl.DeliveryServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryServiceImpl deliveryService;

    public DeliveryController(DeliveryServiceImpl deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/request")
    @PreAuthorize("hasRole('CLIENT') or hasRole('INDIVIDUAL') or hasRole('ENTERPRISE')")
    public ResponseEntity<DeliveryResponseDTO> createDeliveryRequest(
            @Valid @RequestBody DeliveryRequestDTO requestDTO,
            @RequestParam(required = false) String clientId) {

        DeliveryRequest delivery = new DeliveryRequest();
        delivery.setPickupAddress(requestDTO.pickupAddress());
        delivery.setDeliveryAddress(requestDTO.deliveryAddress());
        delivery.setPackageDescription(requestDTO.packageDescription());
        delivery.setPackageWeight(requestDTO.packageWeight());
        delivery.setVehicleId(requestDTO.vehicleId());

        // Simply set the scheduled date, it's already a java.util.Date
        delivery.setScheduledDate(requestDTO.scheduledDate());

        delivery.setAdditionalInstructions(requestDTO.additionalInstructions());
        delivery.setClientId(clientId);
        delivery.setStatus(DeliveryRequest.DeliveryReqStatus.PENDING);
        delivery.setCreatedAt(new Date()); // Set current date

        DeliveryRequest savedDelivery = deliveryService.createDelivery(delivery);

        return new ResponseEntity<>(mapToDTO(savedDelivery), HttpStatus.CREATED);
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