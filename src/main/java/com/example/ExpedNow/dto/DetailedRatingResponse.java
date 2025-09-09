package com.example.ExpedNow.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DetailedRatingResponse(
        String id,
        String deliveryId,
        String clientId,
        Double overallRating,
        String comment,
        List<String> categories,
        String ratingType,
        Double punctualityRating,
        Double professionalismRating,
        Double packageConditionRating,
        Double communicationRating,
        String deliveryPersonFeedback,
        Boolean wouldRecommend,
        List<String> improvements,
        LocalDateTime ratedAt,

        // Informations sur la livraison
        String deliveryPersonName,
        String deliveryAddress,
        LocalDateTime deliveryCompletedAt
) {}