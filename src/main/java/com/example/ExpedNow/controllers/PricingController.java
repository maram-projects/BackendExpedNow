package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.DeliveryRequestDTO;
import com.example.ExpedNow.dto.PricingDetailsResponse;
import com.example.ExpedNow.exception.InvalidRequestException;
import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.models.enums.PackageType;
import com.example.ExpedNow.services.core.DeliveryPricingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private static final Logger logger = LoggerFactory.getLogger(PricingController.class);

    @Autowired
    private DeliveryPricingService pricingService;

    @PostMapping("/calculate")
    public ResponseEntity<?> calculatePrice(
            @Valid @RequestBody DeliveryRequestDTO deliveryRequest,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body("Invalid request data");
        }
        try {
            logger.info("Received pricing request: {}", deliveryRequest);

            // Validate required fields
            if (deliveryRequest.pickupLatitude() == null || deliveryRequest.deliveryLatitude() == null) {
                throw new InvalidRequestException("Location coordinates are required");
            }

            if (deliveryRequest.packageWeight() <= 0) {
                throw new InvalidRequestException("Package weight must be positive");
            }

            DeliveryRequest request = convertToEntity(deliveryRequest);
            PricingDetailsResponse details = pricingService.calculateDetailedPrice(request);
            return ResponseEntity.ok(details);

        } catch (InvalidRequestException e) {
            logger.error("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Server error calculating price", e);
            return ResponseEntity.internalServerError().body("Error calculating price");
        }
    }

    private DeliveryRequest convertToEntity(DeliveryRequestDTO dto) {
        DeliveryRequest entity = new DeliveryRequest();

        // Required fields
        entity.setPickupAddress(dto.pickupAddress());
        entity.setDeliveryAddress(dto.deliveryAddress());
        entity.setPackageDescription(dto.packageDescription());
        entity.setPackageWeight(dto.packageWeight());
        entity.setClientId(dto.clientId());

        // Convert Date to LocalDateTime for scheduledDate
        if (dto.scheduledDate() != null) {
            entity.setScheduledDate(convertToLocalDateTime(dto.scheduledDate()));
        }

        // Optional fields
        entity.setVehicleId(dto.vehicleId());

        // Convert packageType string to enum
        if (dto.packageType() != null) {
            entity.setPackageType(PackageType.valueOf(dto.packageType().toUpperCase()));
        }

        entity.setAdditionalInstructions(dto.additionalInstructions());

        // Location coordinates
        if (dto.pickupLatitude() != null) {
            entity.setPickupLatitude(dto.pickupLatitude());
        }
        if (dto.pickupLongitude() != null) {
            entity.setPickupLongitude(dto.pickupLongitude());
        }
        if (dto.deliveryLatitude() != null) {
            entity.setDeliveryLatitude(dto.deliveryLatitude());
        }
        if (dto.deliveryLongitude() != null) {
            entity.setDeliveryLongitude(dto.deliveryLongitude());
        }

        // Set default status
        entity.setStatus(DeliveryRequest.DeliveryReqStatus.PENDING);

        return entity;
    }

    private LocalDateTime convertToLocalDateTime(Date date) {
        return Instant.ofEpochMilli(date.getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}