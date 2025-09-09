package com.example.ExpedNow.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record DetailedRatingRequest(
        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating cannot exceed 5")
        Double rating,

        String comment,

        List<String> categories, // ponctualite, professionnalisme, etc.

        @NotBlank(message = "Rating type is required")
        String ratingType, // overall, delivery_person, service

        // Évaluations spécifiques par catégorie (optionnel)
        Double punctualityRating,
        Double professionalismRating,
        Double packageConditionRating,
        Double communicationRating,

        // Informations additionnelles
        String deliveryPersonFeedback,
        Boolean wouldRecommend,
        List<String> improvements // suggestions d'amélioration
) {}
