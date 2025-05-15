package com.example.ExpedNow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.util.Date;

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
        Double deliveryLongitude // Optional
) {}