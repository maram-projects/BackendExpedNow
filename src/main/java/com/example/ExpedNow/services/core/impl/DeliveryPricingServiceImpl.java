package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.dto.PricingDetailsResponse;
import com.example.ExpedNow.exception.InvalidRequestException;
import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.services.core.DeliveryPricingService;
import com.example.ExpedNow.services.core.DistanceCalculatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DeliveryPricingServiceImpl implements DeliveryPricingService {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryPricingServiceImpl.class);

    private final DistanceCalculatorService distanceCalculator;
    private final double basePrice;
    private final double distanceRatePerKm;
    private final double weightRatePerKg;
    private final double urgencyFee;
    private final double peakSurchargePercentage;
    private final double holidaySurchargePercentage;
    private final double rule1Bonus;
    private final List<LocalDateTime> holidays; // Example holiday list

    public DeliveryPricingServiceImpl(
            DistanceCalculatorService distanceCalculator,
            @Value("${pricing.base}") double basePrice,
            @Value("${pricing.distance-rate}") double distanceRatePerKm,
            @Value("${pricing.weight-rate}") double weightRatePerKg,
            @Value("${pricing.urgency-fee}") double urgencyFee,
            @Value("${pricing.peak-surcharge}") double peakSurchargePercentage,
            @Value("${pricing.holiday-surcharge}") double holidaySurchargePercentage,
            @Value("${pricing.rules.rule1}") String rule1) {
        this.distanceCalculator = distanceCalculator;
        this.basePrice = basePrice;
        this.distanceRatePerKm = distanceRatePerKm;
        this.weightRatePerKg = weightRatePerKg;
        this.urgencyFee = urgencyFee;
        this.peakSurchargePercentage = peakSurchargePercentage;
        this.holidaySurchargePercentage = holidaySurchargePercentage;
        this.rule1Bonus = parseRule1(rule1);
        this.holidays = initializeHolidays(); // Initialize holidays
    }

    @Override
    public double calculatePrice(DeliveryRequest request) {
        validateDeliveryRequest(request);

        try {
            double distance = calculateDistance(request);
            double baseCost = calculateBaseCost(request, distance);
            double surcharges = calculateSurcharges(request, baseCost);
            double dynamicRulesBonus = applyDynamicRules(request, distance);

            return roundToTwoDecimals(baseCost + surcharges + dynamicRulesBonus);
        } catch (Exception e) {
            logger.error("Error calculating price for delivery request: {}", request.getId(), e);
            throw new InvalidRequestException("Failed to calculate delivery price", e);
        }
    }

    public PricingDetailsResponse calculateDetailedPrice(DeliveryRequest request) {
        validateDeliveryRequest(request);

        PricingDetailsResponse response = new PricingDetailsResponse();

        try {
            double distance = calculateDistance(request);
            response.setDistance(distance);

            // Base calculations
            response.setBasePrice(basePrice);
            response.setDistanceCost(roundToTwoDecimals(distance * distanceRatePerKm));
            response.setWeightCost(roundToTwoDecimals(request.getPackageWeight() * weightRatePerKg));

            double baseTotal = basePrice + response.getDistanceCost() + response.getWeightCost();

            // Time-based surcharges
            LocalDateTime deliveryTime = getDeliveryTime(request);
            response.setPeakSurcharge(calculatePeakSurcharge(deliveryTime, baseTotal));
            response.setHolidaySurcharge(calculateHolidaySurcharge(deliveryTime, baseTotal));
            response.setUrgencyFee(calculateUrgencyFee(request));

            // Dynamic rules
            response.setAppliedRules(getAppliedRules(request, distance));

            // Total calculation
            double total = calculateTotal(response);
            response.setTotalAmount(roundToTwoDecimals(total));

            return response;
        } catch (Exception e) {
            logger.error("Error generating detailed pricing for request: {}", request.getId(), e);
            throw new InvalidRequestException("Failed to generate detailed pricing", e);
        }
    }

    private void validateDeliveryRequest(DeliveryRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Delivery request cannot be null");
        }

        // Validate coordinate ranges for latitude and longitude
        if (!isValidLatitude(request.getPickupLatitude()) ||
                !isValidLongitude(request.getPickupLongitude()) ||
                !isValidLatitude(request.getDeliveryLatitude()) ||
                !isValidLongitude(request.getDeliveryLongitude())) {
            throw new InvalidRequestException("Invalid location coordinates");
        }

        if (request.getPackageWeight() <= 0) {
            throw new InvalidRequestException("Package weight must be positive");
        }
    }

    private boolean isValidLatitude(double latitude) {
        return !Double.isNaN(latitude) && !Double.isInfinite(latitude) && latitude >= -90.0 && latitude <= 90.0;
    }

    private boolean isValidLongitude(double longitude) {
        return !Double.isNaN(longitude) && !Double.isInfinite(longitude) && longitude >= -180.0 && longitude <= 180.0;
    }

    private double calculateDistance(DeliveryRequest request) {
        return distanceCalculator.calculateDistanceKm(
                request.getPickupLatitude(),
                request.getPickupLongitude(),
                request.getDeliveryLatitude(),
                request.getDeliveryLongitude()
        );
    }

    private double calculateBaseCost(DeliveryRequest request, double distance) {
        return basePrice +
                (distance * distanceRatePerKm) +
                (request.getPackageWeight() * weightRatePerKg);
    }

    private double calculateSurcharges(DeliveryRequest request, double baseTotal) {
        double surcharges = 0;
        LocalDateTime deliveryTime = getDeliveryTime(request);

        if (isPeakHours(deliveryTime)) {
            surcharges += baseTotal * peakSurchargePercentage;
        }

        if (isHoliday(deliveryTime)) {
            surcharges += baseTotal * holidaySurchargePercentage;
        }

        if (isUrgentDelivery(request)) {
            surcharges += urgencyFee;
        }

        return surcharges;
    }

    // Added missing applyDynamicRules method
    private double applyDynamicRules(DeliveryRequest request, double distance) {
        double bonus = 0;

        // Apply rule1: Long distance + heavy package bonus
        if (distance > 10 && request.getPackageWeight() > 5) {
            bonus += rule1Bonus;
        }

        // You can add more dynamic rules here in the future

        return bonus;
    }

    private LocalDateTime getDeliveryTime(DeliveryRequest request) {
        return request.getScheduledDate() != null ?
                request.getScheduledDate() :
                LocalDateTime.now();
    }

    private double calculatePeakSurcharge(LocalDateTime time, double baseTotal) {
        return isPeakHours(time) ? baseTotal * peakSurchargePercentage : 0;
    }

    private double calculateHolidaySurcharge(LocalDateTime time, double baseTotal) {
        return isHoliday(time) ? baseTotal * holidaySurchargePercentage : 0;
    }

    private double calculateUrgencyFee(DeliveryRequest request) {
        return isUrgentDelivery(request) ? urgencyFee : 0;
    }

    private List<PricingDetailsResponse.PricingRule> getAppliedRules(DeliveryRequest request, double distance) {
        List<PricingDetailsResponse.PricingRule> rules = new ArrayList<>();

        if (distance > 10 && request.getPackageWeight() > 5) {
            PricingDetailsResponse.PricingRule rule = new PricingDetailsResponse.PricingRule();
            rule.setDescription("Long distance + heavy package bonus");
            rule.setAmount(rule1Bonus);
            rules.add(rule);
        }

        return rules;
    }

    private double calculateTotal(PricingDetailsResponse response) {
        double total = response.getBasePrice() +
                response.getDistanceCost() +
                response.getWeightCost() +
                response.getPeakSurcharge() +
                response.getHolidaySurcharge() +
                response.getUrgencyFee();

        if (response.getAppliedRules() != null) {
            total += response.getAppliedRules().stream()
                    .mapToDouble(r -> r.getAmount())
                    .sum();
        }

        return total;
    }

    private boolean isPeakHours(LocalDateTime time) {
        DayOfWeek day = time.getDayOfWeek();
        int hour = time.getHour();
        return !isWeekend(day) && ((hour >= 7 && hour < 9) || (hour >= 17 && hour < 19));
    }

    private boolean isHoliday(LocalDateTime time) {
        return holidays.stream()
                .anyMatch(holiday ->
                        holiday.getMonth() == time.getMonth() &&
                                holiday.getDayOfMonth() == time.getDayOfMonth());
    }

    private boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private boolean isUrgentDelivery(DeliveryRequest request) {
        if (request.getScheduledDate() == null) return true;
        return request.getScheduledDate().isBefore(LocalDateTime.now().plusHours(2));
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double parseRule1(String rule) {
        try {
            String[] parts = rule.split("=>");
            return Double.parseDouble(parts[1].trim());
        } catch (Exception e) {
            logger.warn("Failed to parse pricing rule, using default value 0.0");
            return 0.0;
        }
    }

    private List<LocalDateTime> initializeHolidays() {
        // This should be replaced with actual holiday data from database or configuration
        List<LocalDateTime> holidays = new ArrayList<>();
        // Example holidays (January 1st, May 1st, etc.)
        holidays.add(LocalDateTime.of(LocalDateTime.now().getYear(), 1, 1, 0, 0));
        holidays.add(LocalDateTime.of(LocalDateTime.now().getYear(), 5, 1, 0, 0));
        return holidays;
    }
}