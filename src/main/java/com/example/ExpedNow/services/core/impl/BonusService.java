package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.*;
import com.example.ExpedNow.models.enums.BonusStatus;
import com.example.ExpedNow.repositories.BonusRepository;
import com.example.ExpedNow.repositories.DeliveryReqRepository;
import com.example.ExpedNow.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

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
        return bonusRepository.save(bonus);
    }

    // رفض المكافأة مع سبب
    public Bonus rejectBonus(String bonusId, String reason) {
        Bonus bonus = bonusRepository.findById(bonusId)
                .orElseThrow(() -> new RuntimeException("المكافأة غير موجودة"));

        bonus.setStatus(BonusStatus.REJECTED);
        bonus.setRejectionReason(reason);

        // إرسال إشعار للموصل بالرفض وسببه
        notificationService.sendBonusNotification(bonus.getDeliveryPersonId(),
                "للأسف، تم رفض مكافأتك الأسبوعية. السبب: " + reason);

        return bonusRepository.save(bonus);
    }

    // دفع المكافأة (بواسطة المدير)
    public Bonus payBonus(String bonusId) {
        Bonus bonus = bonusRepository.findById(bonusId)
                .orElseThrow(() -> new RuntimeException("المكافأة غير موجودة"));

        if (bonus.getStatus() != BonusStatus.APPROVED) {
            throw new RuntimeException("يجب الموافقة على المكافأة أولاً");
        }

        bonus.setStatus(BonusStatus.PAID);
        bonus.setPaidAt(LocalDateTime.now());

        // هنا نضيف المبلغ إلى رصيد الموصل
        User dp = userRepository.findById(bonus.getDeliveryPersonId())
                .orElseThrow(() -> new RuntimeException("الموصل غير موجود"));

        dp.setBalance(dp.getBalance() + bonus.getAmount());
        userRepository.save(dp);

        return bonusRepository.save(bonus);
    }
}