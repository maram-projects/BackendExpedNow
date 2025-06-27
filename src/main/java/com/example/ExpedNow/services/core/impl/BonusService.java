package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.*;
import com.example.ExpedNow.models.enums.BonusStatus;
import com.example.ExpedNow.repositories.BonusRepository;
import com.example.ExpedNow.repositories.DeliveryReqRepository;
import com.example.ExpedNow.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import java.util.HashMap;
import java.util.Map;

@Service
public class BonusService {

    @Autowired
    private BonusRepository bonusRepository;

    @Autowired
    private DeliveryReqRepository deliveryReqRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    // مهمة مجدولة لحساب المكافآت الأسبوعية
    @Scheduled(cron = "0 0 0 ? * MON") // كل يوم اثنين
    public void calculateWeeklyBonuses() {
        LocalDateTime startOfLastWeek = LocalDateTime.now().minusWeeks(1).with(DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfLastWeek = startOfLastWeek.plusDays(6).withHour(23).withMinute(59).withSecond(59);

        // الحصول على جميع الموصلين
        List<User> deliveryPersons = userRepository.findByRole("DELIVERY_PERSON");

        for (User dp : deliveryPersons) {
            // حساب عدد التوصيلات المكتملة الأسبوع الماضي
            // Note: Make sure your DeliveryReqRepository has this method or modify accordingly
            int completedDeliveries = deliveryReqRepository.countByDeliveryPersonIdAndStatusAndCompletedAtBetween(
                    dp.getId(),
                    DeliveryRequest.DeliveryReqStatus.DELIVERED,
                    startOfLastWeek,
                    endOfLastWeek
            );

            // إذا أكمل أكثر من 20 توصيل
            if (completedDeliveries >= 20) {
                Bonus bonus = new Bonus();
                bonus.setDeliveryPersonId(dp.getId());
                bonus.setAmount(calculateBonusAmount(completedDeliveries));
                bonus.setDeliveryCount(completedDeliveries);
                bonus.setStartDate(startOfLastWeek);
                bonus.setEndDate(endOfLastWeek);
                bonus.setStatus(BonusStatus.PENDING);
                bonus.setCreatedAt(LocalDateTime.now());

                bonusRepository.save(bonus);

                // إرسال إشعار للموصل
                notificationService.sendBonusNotification(dp.getId(),
                        "تهانينا! لقد أكملت " + completedDeliveries + " توصيلات الأسبوع الماضي وحصلت على مكافأة بقيمة " +
                                bonus.getAmount() + " د.ت. سيتم إضافتها إلى رصيدك.");
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

    // الموافقة على المكافأة (بواسطة المدير)
    public Bonus approveBonus(String bonusId) {
        Bonus bonus = bonusRepository.findById(bonusId)
                .orElseThrow(() -> new RuntimeException("المكافأة غير موجودة"));

        bonus.setStatus(BonusStatus.APPROVED);
        bonus.setApprovedAt(LocalDateTime.now());
        bonus.setUpdatedAt(LocalDateTime.now());

        return bonusRepository.save(bonus);
    }

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

    // دفع المكافأة (بواسطة المدير)
    @Transactional
    public Bonus payBonus(String bonusId) {
        Bonus bonus = bonusRepository.findById(bonusId)
                .orElseThrow(() -> new RuntimeException("المكافأة غير موجودة"));

        if (bonus.getStatus() != BonusStatus.APPROVED) {
            throw new RuntimeException("يجب الموافقة على المكافأة أولاً");
        }

        User dp = userRepository.findById(bonus.getDeliveryPersonId())
                .orElseThrow(() -> new RuntimeException("الموصل غير موجود"));

        // إضافة المكافأة إلى رصيد الموصل
        dp.setBalance(dp.getBalance() + bonus.getAmount());
        userRepository.save(dp);

        bonus.setStatus(BonusStatus.PAID);
        bonus.setPaidAt(LocalDateTime.now());
        bonus.setUpdatedAt(LocalDateTime.now());

        System.out.println("تم دفع مكافأة " + bonus.getAmount() + " د.ت للموصل " + dp.getId());

        return bonusRepository.save(bonus);
    }

    public Bonus getBonusById(String bonusId) {
        return bonusRepository.findById(bonusId)
                .orElseThrow(() -> new RuntimeException("المكافأة غير موجودة"));
    }

    public Bonus createBonus(Bonus bonus) {
        bonus.setCreatedAt(LocalDateTime.now());
        bonus.setStatus(BonusStatus.PENDING);
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

    @Transactional
    public int bulkApproveBonuses(List<String> bonusIds) {
        int count = 0;
        for (String bonusId : bonusIds) {
            try {
                approveBonus(bonusId);
                count++;
            } catch (Exception e) {
                System.err.println("Failed to approve bonus: " + bonusId + " - " + e.getMessage());
            }
        }
        return count;
    }

    public Map<String, Object> getBonusStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalBonuses = bonusRepository.count();
        Double totalBonusAmount = bonusRepository.sumAllAmounts();

        stats.put("totalBonuses", totalBonuses);
        stats.put("totalAmount", totalBonusAmount != null ? totalBonusAmount : 0.0);

        // Status breakdown
        Map<String, Long> statusBreakdown = new HashMap<>();
        statusBreakdown.put("PENDING", bonusRepository.countByStatus(BonusStatus.PENDING));
        statusBreakdown.put("APPROVED", bonusRepository.countByStatus(BonusStatus.APPROVED));
        statusBreakdown.put("PAID", bonusRepository.countByStatus(BonusStatus.PAID));
        statusBreakdown.put("REJECTED", bonusRepository.countByStatus(BonusStatus.REJECTED));

        stats.put("statusBreakdown", statusBreakdown);
        stats.put("monthlyBreakdown", new HashMap<>());
        stats.put("deliveryPersonBreakdown", new HashMap<>());

        return stats;
    }

    public Map<String, Object> getBonusSummary() {
        Map<String, Object> summary = new HashMap<>();

        summary.put("totalBonuses", bonusRepository.count());
        summary.put("pendingBonuses", bonusRepository.countByStatus(BonusStatus.PENDING));
        summary.put("approvedBonuses", bonusRepository.countByStatus(BonusStatus.APPROVED));
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

        // Status breakdown
        Map<String, Long> statusBreakdown = new HashMap<>();
        statusBreakdown.put("PENDING", bonusRepository.countByStatus(BonusStatus.PENDING));
        statusBreakdown.put("APPROVED", bonusRepository.countByStatus(BonusStatus.APPROVED));
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

        // Get bonus counts
        long pendingBonuses = bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.PENDING);
        long totalBonuses = bonusRepository.countByDeliveryPersonId(deliveryPersonId);
        long paidBonusCount = bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.PAID);
        long approvedBonuses = bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.APPROVED);
        long rejectedBonuses = bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.REJECTED);

        stats.put("weeklyDeliveries", weeklyDeliveries);
        stats.put("monthlyDeliveries", monthlyDeliveries);
        stats.put("totalEarnings", totalEarnings);
        stats.put("pendingBonuses", pendingBonuses);
        stats.put("totalBonuses", totalBonuses);
        stats.put("paidBonuses", paidBonusCount);
        stats.put("approvedBonuses", approvedBonuses);
        stats.put("rejectedBonuses", rejectedBonuses);

        return stats;
    }

    // BonusService.java
    public Map<String, Object> getDeliveryPersonSummary(String deliveryPersonId) {
        Map<String, Object> summary = new HashMap<>();

        // Get counts per status
        summary.put("totalBonuses", bonusRepository.countByDeliveryPersonId(deliveryPersonId));
        summary.put("pendingBonuses", bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.PENDING));
        summary.put("approvedBonuses", bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.APPROVED));
        summary.put("paidBonuses", bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.PAID));
        summary.put("rejectedBonuses", bonusRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.REJECTED));

        // Get amounts
        Double totalAmount = bonusRepository.sumAmountByDeliveryPersonId(deliveryPersonId);
        Double paidAmount = bonusRepository.sumAmountByDeliveryPersonIdAndStatus(deliveryPersonId, BonusStatus.PAID);

        summary.put("totalAmount", totalAmount != null ? totalAmount : 0.0);
        summary.put("paidAmount", paidAmount != null ? paidAmount : 0.0);

        return summary;
    }
}