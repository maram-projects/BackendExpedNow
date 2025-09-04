package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.*;
import com.example.ExpedNow.models.enums.BonusStatus;
import com.example.ExpedNow.repositories.BonusRepository;
import com.example.ExpedNow.repositories.DeliveryReqRepository;
import com.example.ExpedNow.repositories.MissionRepository;
import com.example.ExpedNow.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import java.util.HashMap;
import java.util.Map;

@Service
public class BonusService {

    private static final Logger logger = LoggerFactory.getLogger(BonusService.class);

    @Autowired
    private BonusRepository bonusRepository;

    @Autowired
    private DeliveryReqRepository deliveryReqRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MissionRepository missionRepository;

    @Scheduled(cron = "0 0 0 ? * MON")
    public void calculateWeeklyBonuses() {
        LocalDateTime startOfLastWeek = LocalDateTime.now().minusWeeks(1).with(DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfLastWeek = startOfLastWeek.plusDays(6).withHour(23).withMinute(59).withSecond(59);

        List<User> deliveryPersons = userRepository.findByRole("DELIVERY_PERSON");

        for (User dp : deliveryPersons) {
            int completedDeliveries = deliveryReqRepository.countByDeliveryPersonIdAndStatusAndCompletedAtBetween(
                    dp.getId(),
                    DeliveryRequest.DeliveryReqStatus.DELIVERED,
                    startOfLastWeek,
                    endOfLastWeek
            );

            if (completedDeliveries >= 20) {
                Bonus bonus = new Bonus();
                bonus.setDeliveryPersonId(dp.getId());
                bonus.setAmount(calculateBonusAmount(completedDeliveries));
                bonus.setDeliveryCount(completedDeliveries);
                bonus.setStartDate(startOfLastWeek);
                bonus.setEndDate(endOfLastWeek);
                bonus.setStatus(BonusStatus.CREATED);
                bonus.setCreatedAt(LocalDateTime.now());

                bonusRepository.save(bonus);

                notificationService.sendBonusNotification(dp.getId(),
                        "Congratulations! You completed " + completedDeliveries + " deliveries last week and earned a bonus of " +
                                bonus.getAmount() + " TND. Admin can now pay it.");
            }
        }
    }

    // حساب قيمة المكافأة
    private double calculateBonusAmount(int deliveryCount) {
        double baseAmount = 50; // مبلغ أساسي
        double perDelivery = 2; // مبلغ لكل توصيل إضافي بعد 20

        if (deliveryCount <= 20) {
            return baseAmount;
        } else {
            return baseAmount + (deliveryCount - 20) * perDelivery;
        }
    }

    // الحصول على جميع المكافآت
    public List<Bonus> getAllBonuses() {
        return bonusRepository.findAll();
    }

    // الحصول على مكافآت موصل معين
    public List<Bonus> getDeliveryPersonBonuses(String deliveryPersonId) {
        return bonusRepository.findByDeliveryPersonId(deliveryPersonId);
    }

    // This method is no longer needed since we removed APPROVED status
    // If you need approval functionality, you can modify the payBonus method to handle approval logic

    // رفض المكافأة مع سبب
    public Bonus rejectBonus(String bonusId, String reason) {
        Bonus bonus = bonusRepository.findById(bonusId)
                .orElseThrow(() -> new RuntimeException("المكافأة غير موجودة"));

        bonus.setStatus(BonusStatus.REJECTED);
        bonus.setRejectionReason(reason);
        bonus.setRejectedAt(LocalDateTime.now());
        bonus.setUpdatedAt(LocalDateTime.now());

        // إرسال إشعار للموصل بالرفض وسببه
        notificationService.sendBonusNotification(bonus.getDeliveryPersonId(),
                "للأسف، تم رفض مكافأتك الأسبوعية. السبب: " + reason);

        return bonusRepository.save(bonus);
    }

    @Transactional
    public Bonus payBonus(String bonusId) {
        Bonus bonus = bonusRepository.findById(bonusId)
                .orElseThrow(() -> new RuntimeException("Bonus not found"));

        // Can only pay CREATED bonuses
        if (bonus.getStatus() != BonusStatus.CREATED) {
            throw new RuntimeException("Cannot pay this bonus. Status must be CREATED");
        }

        User dp = userRepository.findById(bonus.getDeliveryPersonId())
                .orElseThrow(() -> new RuntimeException("Delivery person not found"));

        // Add bonus to delivery person's balance
        dp.setBalance(dp.getBalance() + bonus.getAmount());
        userRepository.save(dp);

        bonus.setStatus(BonusStatus.PAID);
        bonus.setPaidAt(LocalDateTime.now());
        bonus.setUpdatedAt(LocalDateTime.now());

        System.out.println("Paid bonus " + bonus.getAmount() + " to delivery person " + dp.getId());

        return bonusRepository.save(bonus);
    }

    public Bonus getBonusById(String bonusId) {
        return bonusRepository.findById(bonusId)
                .orElseThrow(() -> new RuntimeException("المكافأة غير موجودة"));
    }

    public Bonus createBonus(Bonus bonus) {
        bonus.setCreatedAt(LocalDateTime.now());
        bonus.setStatus(BonusStatus.CREATED); // Changed from PENDING to CREATED
        return bonusRepository.save(bonus);
    }

    public Bonus updateBonus(String bonusId, Bonus updatedBonus) {
        Bonus bonus = getBonusById(bonusId);

        if (updatedBonus.getAmount() > 0) {
            bonus.setAmount(updatedBonus.getAmount());
        }
        if (updatedBonus.getDeliveryCount() > 0) {
            bonus.setDeliveryCount(updatedBonus.getDeliveryCount());
        }
        if (updatedBonus.getStartDate() != null) {
            bonus.setStartDate(updatedBonus.getStartDate());
        }
        if (updatedBonus.getEndDate() != null) {
            bonus.setEndDate(updatedBonus.getEndDate());
        }
        if (updatedBonus.getStatus() != null) {
            bonus.setStatus(updatedBonus.getStatus());
        }

        bonus.setUpdatedAt(LocalDateTime.now());
        return bonusRepository.save(bonus);
    }

    public Bonus cancelBonus(String bonusId) {
        Bonus bonus = getBonusById(bonusId);
        bonus.setStatus(BonusStatus.REJECTED);
        bonus.setUpdatedAt(LocalDateTime.now());
        return bonusRepository.save(bonus);
    }

    public Bonus updateBonusStatus(String bonusId, BonusStatus status) {
        Bonus bonus = getBonusById(bonusId);
        bonus.setStatus(status);
        bonus.setUpdatedAt(LocalDateTime.now());
        return bonusRepository.save(bonus);
    }

    public List<Bonus> getBonusesByStatus(BonusStatus status) {
        return bonusRepository.findByStatus(status);
    }

    public List<Bonus> getAllBonusesWithFilters(BonusStatus status, String deliveryPersonId,
                                                String startDate, String endDate, Pageable pageable) {
        if (status != null && deliveryPersonId != null) {
            return bonusRepository.findByStatusAndDeliveryPersonId(status, deliveryPersonId);
        } else if (status != null) {
            return bonusRepository.findByStatus(status);
        } else if (deliveryPersonId != null) {
            return bonusRepository.findByDeliveryPersonId(deliveryPersonId);
        }
        return bonusRepository.findAll();
    }

    public List<Bonus> getBonusHistory(String deliveryPersonId, Integer limit) {
        List<Bonus> bonuses = bonusRepository.findByDeliveryPersonIdOrderByCreatedAtDesc(deliveryPersonId);
        if (limit != null && limit > 0) {
            return bonuses.stream().limit(limit).collect(Collectors.toList());
        }
        return bonuses;
    }

    public List<Bonus> searchBonuses(BonusStatus status, String deliveryPersonId,
                                     String startDate, String endDate) {
        return getAllBonusesWithFilters(status, deliveryPersonId, startDate, endDate, null);
    }

    // Removed bulkApproveBonuses method since APPROVED status doesn't exist
    // If you need bulk operations, you can create bulkPayBonuses or similar

    public Map<String, Object> getBonusStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            long totalBonuses = bonusRepository.count();
            Double totalBonusAmount = bonusRepository.sumAllAmounts();

            stats.put("totalBonuses", totalBonuses);
            stats.put("totalAmount", totalBonusAmount != null ? totalBonusAmount : 0.0);

            // Status breakdown - only use existing statuses with error handling
            Map<String, Long> statusBreakdown = new HashMap<>();

            try {
                statusBreakdown.put("CREATED", bonusRepository.countByStatus(BonusStatus.CREATED));
            } catch (Exception e) {
                statusBreakdown.put("CREATED", 0L);
            }

            try {
                statusBreakdown.put("PAID", bonusRepository.countByStatus(BonusStatus.PAID));
            } catch (Exception e) {
                statusBreakdown.put("PAID", 0L);
            }

            try {
                statusBreakdown.put("REJECTED", bonusRepository.countByStatus(BonusStatus.REJECTED));
            } catch (Exception e) {
                statusBreakdown.put("REJECTED", 0L);
            }

            stats.put("statusBreakdown", statusBreakdown);

        } catch (Exception e) {
            System.err.println("Error getting bonus stats: " + e.getMessage());
            // Return default stats structure
            stats.put("totalBonuses", 0L);
            stats.put("totalAmount", 0.0);
            Map<String, Long> statusBreakdown = new HashMap<>();
            statusBreakdown.put("CREATED", 0L);
            statusBreakdown.put("PAID", 0L);
            statusBreakdown.put("REJECTED", 0L);
            stats.put("statusBreakdown", statusBreakdown);
        }

        stats.put("monthlyBreakdown", new HashMap<>());
        stats.put("deliveryPersonBreakdown", new HashMap<>());

        return stats;
    }
    public Map<String, Object> getBonusSummary() {
        Map<String, Object> summary = new HashMap<>();

        summary.put("totalBonuses", bonusRepository.count());
        summary.put("createdBonuses", bonusRepository.countByStatus(BonusStatus.CREATED));
        summary.put("paidBonuses", bonusRepository.countByStatus(BonusStatus.PAID));
        summary.put("rejectedBonuses", bonusRepository.countByStatus(BonusStatus.REJECTED));

        return summary;
    }

    @Transactional
    public int bulkPayBonuses(List<String> bonusIds) {
        int count = 0;
        for (String bonusId : bonusIds) {
            try {
                payBonus(bonusId);
                count++;
            } catch (Exception e) {
                System.err.println("Failed to pay bonus: " + bonusId + " - " + e.getMessage());
            }
        }
        return count;
    }

    /**
     * Get all bonuses with error handling for old status values
     */
    public List<Bonus> getAllBonusesWithErrorHandling() {
        try {
            return bonusRepository.findAll();
        } catch (Exception e) {
            System.err.println("Error fetching bonuses, possibly due to old status values: " + e.getMessage());

            // Try to get bonuses individually and filter out problematic ones
            List<Bonus> validBonuses = new ArrayList<>();

            // Get by each valid status
            try {
                validBonuses.addAll(bonusRepository.findByStatus(BonusStatus.CREATED));
            } catch (Exception ex) { /* ignore */ }

            try {
                validBonuses.addAll(bonusRepository.findByStatus(BonusStatus.PAID));
            } catch (Exception ex) { /* ignore */ }

            try {
                validBonuses.addAll(bonusRepository.findByStatus(BonusStatus.REJECTED));
            } catch (Exception ex) { /* ignore */ }

            return validBonuses;
        }
    }
    @Transactional
    public int bulkRejectBonuses(List<String> bonusIds, String reason) {
        int count = 0;
        for (String bonusId : bonusIds) {
            try {
                rejectBonus(bonusId, reason);
                count++;
            } catch (Exception e) {
                System.err.println("Failed to reject bonus: " + bonusId + " - " + e.getMessage());
            }
        }
        return count;
    }

    public Map<String, Object> calculatePotentialBonus(String deliveryPersonId) {
        Map<String, Object> result = new HashMap<>();

        // Get current week's deliveries
        LocalDateTime startOfWeek = LocalDateTime.now().with(DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfWeek = startOfWeek.plusDays(6).withHour(23).withMinute(59).withSecond(59);

        int currentWeekDeliveries = deliveryReqRepository.countByDeliveryPersonIdAndStatusAndCompletedAtBetween(
                deliveryPersonId,
                DeliveryRequest.DeliveryReqStatus.DELIVERED,
                startOfWeek,
                endOfWeek
        );

        double potentialBonus = 0.0;
        if (currentWeekDeliveries >= 20) {
            potentialBonus = calculateBonusAmount(currentWeekDeliveries);
        }

        Map<String, Object> criteria = new HashMap<>();
        criteria.put("minimumDeliveries", 20);
        criteria.put("currentWeekDeliveries", currentWeekDeliveries);
        criteria.put("remainingDeliveries", Math.max(0, 20 - currentWeekDeliveries));
        criteria.put("eligible", currentWeekDeliveries >= 20);

        result.put("success", true);
        result.put("potentialBonus", potentialBonus);
        result.put("criteria", criteria);

        return result;
    }

    public List<Bonus> searchBonusesWithDateRange(BonusStatus status, String deliveryPersonId,
                                                  LocalDateTime startDate, LocalDateTime endDate) {
        if (status != null && deliveryPersonId != null && startDate != null && endDate != null) {
            return bonusRepository.findByStatusAndDeliveryPersonIdAndCreatedAtBetween(
                    status, deliveryPersonId, startDate, endDate);
        } else if (startDate != null && endDate != null) {
            return bonusRepository.findByCreatedAtBetween(startDate, endDate);
        }
        return getAllBonusesWithFilters(status, deliveryPersonId, null, null, null);
    }

    public Map<String, Object> getEnhancedBonusStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalBonuses = bonusRepository.count();
        Double totalAmountSum = bonusRepository.sumAllAmounts();

        stats.put("totalBonuses", totalBonuses);
        stats.put("totalAmount", totalAmountSum != null ? totalAmountSum : 0.0);

        // Status breakdown - only use existing statuses
        Map<String, Long> statusBreakdown = new HashMap<>();
        statusBreakdown.put("CREATED", bonusRepository.countByStatus(BonusStatus.CREATED));
        statusBreakdown.put("PAID", bonusRepository.countByStatus(BonusStatus.PAID));
        statusBreakdown.put("REJECTED", bonusRepository.countByStatus(BonusStatus.REJECTED));

        stats.put("statusBreakdown", statusBreakdown);

        // Monthly breakdown (last 12 months)
        Map<String, Double> monthlyBreakdown = new HashMap<>();
        LocalDateTime twelveMonthsAgo = LocalDateTime.now().minusMonths(12);

        for (int i = 0; i < 12; i++) {
            LocalDateTime monthStart = twelveMonthsAgo.plusMonths(i).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime monthEnd = monthStart.plusMonths(1).minusDays(1).withHour(23).withMinute(59).withSecond(59);

            String monthKey = monthStart.getMonth().toString() + "_" + monthStart.getYear();

            Double monthlyTotal = bonusRepository.sumAmountByCreatedAtBetween(monthStart, monthEnd);
            monthlyBreakdown.put(monthKey, monthlyTotal != null ? monthlyTotal : 0.0);
        }

        stats.put("monthlyBreakdown", monthlyBreakdown);

        // Delivery person breakdown - simplified version
        Map<String, Double> deliveryPersonBreakdown = new HashMap<>();
        List<User> deliveryPersons = userRepository.findByRole("DELIVERY_PERSON");

        for (User dp : deliveryPersons) {
            Double personalTotalAmount = bonusRepository.sumAmountByDeliveryPersonId(dp.getId());
            if (personalTotalAmount != null && personalTotalAmount > 0) {
                deliveryPersonBreakdown.put(dp.getId(), personalTotalAmount);
            }
        }

        stats.put("deliveryPersonBreakdown", deliveryPersonBreakdown);

        return stats;
    }

    public Map<String, Object> getDeliveryStats(String deliveryPersonId) {
        Map<String, Object> stats = new HashMap<>();

        // Current week deliveries
        LocalDateTime startOfWeek = LocalDateTime.now().with(DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfWeek = startOfWeek.plusDays(6).withHour(23).withMinute(59).withSecond(59);

        int weeklyDeliveries = deliveryReqRepository.countByDeliveryPersonIdAndStatusAndCompletedAtBetween(
                deliveryPersonId,
                DeliveryRequest.DeliveryReqStatus.DELIVERED,
                startOfWeek,
                endOfWeek
        );

        // Current month deliveries
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1).minusDays(1).withHour(23).withMinute(59).withSecond(59);

        int monthlyDeliveries = deliveryReqRepository.countByDeliveryPersonIdAndStatusAndCompletedAtBetween(
                deliveryPersonId,
                DeliveryRequest.DeliveryReqStatus.DELIVERED,
                startOfMonth,
                endOfMonth
        );

        // Calculate total earnings from paid bonuses
        List<Bonus> paidBonuses = bonusRepository.findByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.PAID);
        double totalEarnings = paidBonuses.stream().mapToDouble(Bonus::getAmount).sum();

        // Get bonus counts - only use existing statuses
        long createdBonuses = bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.CREATED);
        long totalBonuses = bonusRepository.countByDeliveryPersonId(deliveryPersonId);
        long paidBonusCount = bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.PAID);
        long rejectedBonuses = bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.REJECTED);

        stats.put("weeklyDeliveries", weeklyDeliveries);
        stats.put("monthlyDeliveries", monthlyDeliveries);
        stats.put("totalEarnings", totalEarnings);
        stats.put("createdBonuses", createdBonuses); // Changed from pendingBonuses
        stats.put("totalBonuses", totalBonuses);
        stats.put("paidBonuses", paidBonusCount);
        stats.put("rejectedBonuses", rejectedBonuses);

        return stats;
    }

    public Map<String, Object> getDeliveryPersonSummary(String deliveryPersonId) {
        Map<String, Object> summary = new HashMap<>();

        // Get counts per status - only use existing statuses
        summary.put("totalBonuses", bonusRepository.countByDeliveryPersonId(deliveryPersonId));
        summary.put("createdBonuses", bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.CREATED));
        summary.put("paidBonuses", bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.PAID));
        summary.put("rejectedBonuses", bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.REJECTED));

        // Get amounts
        Double totalAmount = bonusRepository.sumAmountByDeliveryPersonId(deliveryPersonId);
        Double paidAmount = bonusRepository.sumAmountByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.PAID);

        summary.put("totalAmount", totalAmount != null ? totalAmount : 0.0);
        summary.put("paidAmount", paidAmount != null ? paidAmount : 0.0);

        return summary;
    }

    /**
     * Get mission progress toward bonus milestones
     */
    public Map<String, Object> getMissionProgressTowardBonus(String deliveryPersonId) {
        Map<String, Object> progress = new HashMap<>();

        try {
            // Get completed missions count
            long completedMissions = missionRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, "COMPLETED");

            // Calculate progress toward next milestone
            long progressToNextBonus = completedMissions % 10;
            long nextMilestoneTarget = ((completedMissions / 10) + 1) * 10;

            // Calculate potential earnings
            long milestonesEarned = completedMissions / 10;
            double totalEarningsPotential = milestonesEarned * 100.0; // $100 per milestone

            progress.put("completedMissions", completedMissions);
            progress.put("progressToNextBonus", progressToNextBonus);
            progress.put("nextBonusTarget", nextMilestoneTarget);
            progress.put("totalEarningsPotential", totalEarningsPotential);
            progress.put("milestonesReached", milestonesEarned);

        } catch (Exception e) {
            logger.error("Error getting mission progress for delivery person {}: {}", deliveryPersonId, e.getMessage());
            progress.put("completedMissions", 0);
            progress.put("progressToNextBonus", 0);
            progress.put("nextBonusTarget", 10);
            progress.put("totalEarningsPotential", 0.0);
            progress.put("milestonesReached", 0);
        }

        return progress;
    }

    /**
     * Check milestone eligibility
     */
    public Map<String, Object> checkMilestoneEligibility(String deliveryPersonId) {
        Map<String, Object> eligibility = new HashMap<>();

        try {
            long completedMissions = missionRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, "COMPLETED");
            long currentMilestone = completedMissions / 10;
            boolean isEligible = completedMissions > 0 && completedMissions % 10 == 0;

            // Check if bonus already exists for this milestone
            if (isEligible) {
                String milestoneReason = "10 Mission Milestone Bonus - " + completedMissions + " missions completed";
                List<Bonus> existingBonuses = bonusRepository.findByDeliveryPersonIdAndReason(deliveryPersonId, milestoneReason);
                if (!existingBonuses.isEmpty()) {
                    isEligible = false; // Already has bonus for this milestone
                }
            }

            eligibility.put("isEligible", isEligible);
            eligibility.put("completedMissions", completedMissions);
            eligibility.put("milestoneReached", currentMilestone);
            eligibility.put("bonusAmount", isEligible ? 100.0 : 0.0);
            eligibility.put("nextMilestoneAt", (currentMilestone + 1) * 10);

        } catch (Exception e) {
            logger.error("Error checking milestone eligibility for delivery person {}: {}", deliveryPersonId, e.getMessage());
            eligibility.put("isEligible", false);
            eligibility.put("completedMissions", 0);
            eligibility.put("milestoneReached", 0);
            eligibility.put("bonusAmount", 0.0);
            eligibility.put("nextMilestoneAt", 10);
        }

        return eligibility;
    }

    /**
     * Get comprehensive delivery person statistics
     */
    public Map<String, Object> getComprehensiveDeliveryPersonStats(String deliveryPersonId) {
        Map<String, Object> stats = new HashMap<>();

        try {
            // Mission statistics
            long totalMissions = missionRepository.countByDeliveryPersonId(deliveryPersonId);
            long completedMissions = missionRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, "COMPLETED");
            long pendingMissions = missionRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, "PENDING");
            long inProgressMissions = missionRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, "IN_PROGRESS");

            // Bonus statistics
            long totalBonuses = bonusRepository.countByDeliveryPersonId(deliveryPersonId);
            long createdBonuses = bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.CREATED);
            long paidBonuses = bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.PAID);

            Double totalEarnings = bonusRepository.sumAmountByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.PAID);
            if (totalEarnings == null) totalEarnings = 0.0;

            // Milestone calculations
            long milestonesReached = completedMissions / 10;
            long nextMilestoneProgress = completedMissions % 10;
            long nextMilestoneTarget = (milestonesReached + 1) * 10;

            // Milestone bonuses specifically
            long milestoneBonuses = bonusRepository.countByDeliveryPersonIdAndBonusType(deliveryPersonId, "MILESTONE");

            stats.put("totalMissions", totalMissions);
            stats.put("completedMissions", completedMissions);
            stats.put("pendingMissions", pendingMissions);
            stats.put("inProgressMissions", inProgressMissions);
            stats.put("totalBonuses", totalBonuses);
            stats.put("createdBonuses", createdBonuses);
            stats.put("paidBonuses", paidBonuses);
            stats.put("totalEarnings", totalEarnings);
            stats.put("milestonesReached", milestonesReached);
            stats.put("nextMilestoneProgress", nextMilestoneProgress);
            stats.put("nextMilestoneTarget", nextMilestoneTarget);
            stats.put("milestoneBonuses", milestoneBonuses);

        } catch (Exception e) {
            logger.error("Error getting comprehensive stats for delivery person {}: {}", deliveryPersonId, e.getMessage());
            // Return default values
            stats.put("totalMissions", 0);
            stats.put("completedMissions", 0);
            stats.put("pendingMissions", 0);
            stats.put("inProgressMissions", 0);
            stats.put("totalBonuses", 0);
            stats.put("createdBonuses", 0);
            stats.put("paidBonuses", 0);
            stats.put("totalEarnings", 0.0);
            stats.put("milestonesReached", 0);
            stats.put("nextMilestoneProgress", 0);
            stats.put("nextMilestoneTarget", 10);
            stats.put("milestoneBonuses", 0);
        }

        return stats;
    }

    /**
     * Get milestone bonuses for delivery person
     */
    public List<Bonus> getMilestoneBonuses(String deliveryPersonId) {
        try {
            return bonusRepository.findByDeliveryPersonIdAndBonusType(deliveryPersonId, "MILESTONE");
        } catch (Exception e) {
            logger.error("Error getting milestone bonuses for delivery person {}: {}", deliveryPersonId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Perform manual milestone check (for admin use)
     */
    public Map<String, Object> performManualMilestoneCheck(String deliveryPersonId) {
        Map<String, Object> result = new HashMap<>();

        try {
            long completedMissions = missionRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, "COMPLETED");

            if (completedMissions > 0 && completedMissions % 10 == 0) {
                // Check if bonus already exists
                String milestoneReason = "10 Mission Milestone Bonus - " + completedMissions + " missions completed";
                List<Bonus> existingBonuses = bonusRepository.findByDeliveryPersonIdAndReason(deliveryPersonId, milestoneReason);

                if (existingBonuses.isEmpty()) {
                    // Get user details
                    User deliveryPerson = userRepository.findById(deliveryPersonId)
                            .orElseThrow(() -> new RuntimeException("Delivery person not found"));

                    // Create milestone bonus
                    Bonus milestoneBonus = new Bonus();
                    milestoneBonus.setDeliveryPersonId(deliveryPersonId);
                    milestoneBonus.setAmount(100.0);
                    milestoneBonus.setReason(milestoneReason);
                    milestoneBonus.setDescription("Manual milestone bonus for completing " + completedMissions + " missions");
                    milestoneBonus.setCriteria("Complete 10 missions");
                    milestoneBonus.setBonusType("MILESTONE");
                    milestoneBonus.setType("MILESTONE");
                    milestoneBonus.setStatus(BonusStatus.CREATED);
                    milestoneBonus.setCreatedBy("ADMIN_MANUAL");
                    milestoneBonus.setDeliveryPersonName(deliveryPerson.getFullName());
                    milestoneBonus.setDeliveryPersonEmail(deliveryPerson.getEmail());
                    milestoneBonus.setCreatedAt(LocalDateTime.now());

                    Bonus createdBonus = bonusRepository.save(milestoneBonus);

                    result.put("bonusCreated", true);
                    result.put("bonus", createdBonus);
                    result.put("message", "Milestone bonus created successfully");
                } else {
                    result.put("bonusCreated", false);
                    result.put("message", "Milestone bonus already exists for this level");
                    result.put("existingBonus", existingBonuses.get(0));
                }
            } else {
                result.put("bonusCreated", false);
                result.put("message", "No milestone reached or missions not divisible by 10");
            }

            result.put("completedMissions", completedMissions);
            result.put("success", true);

        } catch (Exception e) {
            logger.error("Error performing manual milestone check for delivery person {}: {}", deliveryPersonId, e.getMessage());
            result.put("success", false);
            result.put("bonusCreated", false);
            result.put("message", "Error: " + e.getMessage());
        }

        return result;
    }
}