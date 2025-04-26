package com.example.ExpedNow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;

import java.util.Date;

public record DeliveryRequestDTO(
        @NotBlank String pickupAddress,
        @NotBlank String deliveryAddress,
        @NotBlank String packageDescription,
        @Positive double packageWeight,
        String vehicleId, // No validation since it's optional
        @FutureOrPresent
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Date scheduledDate,
        String packageType,
        String additionalInstructions,
        Double pickupLatitude,
        Double pickupLongitude,
        Double deliveryLatitude,
        Double deliveryLongitude
){}