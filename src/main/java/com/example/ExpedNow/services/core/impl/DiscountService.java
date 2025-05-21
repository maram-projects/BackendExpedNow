package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.*;
import com.example.ExpedNow.models.enums.DiscountType;
import com.example.ExpedNow.repositories.DiscountRepository;
import com.example.ExpedNow.repositories.DeliveryReqRepository;
import com.example.ExpedNow.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DiscountService {

    @Autowired
    private DiscountRepository discountRepository;

    @Autowired
    private DeliveryReqRepository deliveryReqRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    // مهمة مجدولة لتطبيق خصومات الولاء
    @Scheduled(cron = "0 0 0 1 * ?") // أول يوم من كل شهر
    public void applyLoyaltyDiscounts() {
        // الحصول على جميع العملاء الذين لديهم أكثر من 30 طلب في الشهر الماضي
        LocalDateTime startOfLastMonth = LocalDateTime.now().minusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfLastMonth = LocalDateTime.now().withDayOfMonth(1).minusDays(1).withHour(23).withMinute(59).withSecond(59);

        List<User> activeClients = userRepository.findClientsWithMinDeliveries(30, startOfLastMonth, endOfLastMonth);

        for (User client : activeClients) {
            // إنشاء خصم جديد
            Discount discount = new Discount();
            discount.setCode(generateDiscountCode());
            discount.setClientId(client.getId());
            discount.setPercentage(15); // 15% خصم
            discount.setValidFrom(LocalDateTime.now());
            discount.setValidUntil(LocalDateTime.now().plusMonths(1));
            discount.setType(DiscountType.LOYALTY);
            discount.setDescription("خصم ولاء للعميل المتميز");

            discountRepository.save(discount);

            // إرسال إشعار للعميل
            notificationService.sendDiscountNotification(client.getId(),
                    "تهانينا! لقد حصلت على خصم 15% على طلبك القادم بسبب نشاطك الشهري المتميز. كود الخصم: " + discount.getCode());
        }
    }
    // Add this method to your DiscountService class
    public Discount createDiscount(Discount discount) {
        if (discount.getCode() == null || discount.getCode().isEmpty()) {
            discount.setCode(generateDiscountCode());
        }
        if (discount.getValidFrom() == null) {
            discount.setValidFrom(LocalDateTime.now());
        }
        return discountRepository.save(discount);
    }
    // توليد كود خصم عشوائي
    private String generateDiscountCode() {
        return "EXPED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // التحقق من صحة كود الخصم
    public Discount validateDiscount(String code, String clientId) {
        Discount discount = discountRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("كود الخصم غير صالح"));

        if (!discount.getClientId().equals(clientId)) {
            throw new RuntimeException("هذا الكود غير مخصص لك");
        }

        if (discount.isUsed()) {
            throw new RuntimeException("تم استخدام هذا الكود مسبقاً");
        }

        if (LocalDateTime.now().isAfter(discount.getValidUntil())) {
            throw new RuntimeException("انتهت صلاحية هذا الكود");
        }

        return discount;
    }

    // استخدام الخصم
    public Discount useDiscount(String code, String clientId, String orderId) {
        Discount discount = validateDiscount(code, clientId);

        discount.setUsed(true);
        discount.setUsedAt(LocalDateTime.now());
        discount.setUsedForOrderId(orderId);

        return discountRepository.save(discount);
    }

    // الحصول على خصومات العميل النشطة
    public List<Discount> getActiveDiscountsForClient(String clientId) {
        try {
            System.out.println("Fetching discounts for client: " + clientId);
            List<Discount> discounts = discountRepository.findByClientIdAndValidUntilAfterAndUsed(
                    clientId,
                    LocalDateTime.now(),
                    false
            );
            System.out.println("Found discounts: " + discounts.size());
            return discounts;
        } catch (Exception e) {
            System.err.println("Error fetching discounts: " + e.getMessage());
            throw e; // bch ta3ref l'erreur fl'front
        }
    }

    public List<Discount> getAllDiscounts() {
        return discountRepository.findAll();
    }

    public void deleteDiscount(String id) {
        discountRepository.deleteById(id);
    }
}