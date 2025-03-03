package com.example.ExpedNow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;

import java.util.Date;

public record DeliveryRequestDTO(
        @NotBlank String pickupAddress,
        @NotBlank String deliveryAddress,
        @NotBlank String packageDescription,
        @Positive double packageWeight,
        @NotBlank String vehicleId,
        @FutureOrPresent
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Date scheduledDate,
        String additionalInstructions
) {}
