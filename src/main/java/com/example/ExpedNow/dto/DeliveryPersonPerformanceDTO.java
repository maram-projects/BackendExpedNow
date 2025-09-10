package com.example.ExpedNow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
// Create the DTO class
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public  class DeliveryPersonPerformanceDTO {
    private String id;
    private String name;
    private String email;
    private int totalRatings;
    private double averageRating;
    private int completedDeliveries;
    private int positiveRatings;
    private int negativeRatings;
    private double punctualityAverage;
    private double professionalismAverage;
    private double packageConditionAverage;
    private double communicationAverage;
    private double recommendationRate;
    private int thisMonthRatings;
    private int lastMonthRatings;
    private String performanceStatus;
    private Date lastActive;
    private boolean available;
    private double successScore;
    private int totalDeliveries;
}