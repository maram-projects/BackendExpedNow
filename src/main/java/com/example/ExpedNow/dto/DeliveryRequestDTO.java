package com.example.ExpedNow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.Date;

// DeliveryRequestDTO.java
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeliveryRequestDTO(
        @NotBlank String pickupAddress,
        @NotBlank String deliveryAddress,
        @NotBlank String packageDescription,
        @Positive double packageWeight,
        String vehicleId, // Optional
        @FutureOrPresent
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Date scheduledDate,
        String packageType, // Optional
        String additionalInstructions, // Optional
        Double pickupLatitude, // Optional
        Double pickupLongitude, // Optional
        Double deliveryLatitude, // Optional
        Double deliveryLongitude, // Optional
        String clientId,
        String recipientName // Add the missing field
) {
}