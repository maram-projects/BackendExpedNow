// PaymentService.java
package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.*;
import com.example.ExpedNow.models.enums.PaymentStatus;
import com.example.ExpedNow.repositories.DeliveryReqRepository;
import com.example.ExpedNow.repositories.DiscountRepository;
import com.example.ExpedNow.repositories.PaymentRepository;
import com.example.ExpedNow.services.core.PaymentServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class PaymentServiceImpl implements PaymentServiceInterface {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DeliveryReqRepository deliveryReqRepository;

    @Autowired
    private DiscountRepository discountRepository;

    @Override
    public Payment createPayment(Payment payment) {
        // التحقق من وجود طلب التوصيل
        DeliveryRequest delivery = deliveryReqRepository.findById(payment.getDeliveryId())
                .orElseThrow(() -> new RuntimeException("Delivery request not found"));
// تعيين القيم من طلب التوصيل
        payment.setAmount(delivery.getAmount());
        payment.setFinalAmountAfterDiscount(delivery.getFinalAmountAfterDiscount());
        // تعيين القيم الافتراضية
        payment.setPaymentDate(LocalDateTime.now());
        payment.setStatus(PaymentStatus.PENDING);

        // حفظ عملية الدفع
        Payment savedPayment = paymentRepository.save(payment);

        // تحديث حالة طلب التوصيل
        delivery.setPaymentId(savedPayment.getId());
        delivery.setPaymentStatus(PaymentStatus.PENDING);
        deliveryReqRepository.save(delivery);

        return savedPayment;
    }

    @Override
    public Payment processPayment(String paymentId, String discountCode) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        DeliveryRequest delivery = deliveryReqRepository.findById(payment.getDeliveryId())
                .orElseThrow(() -> new RuntimeException("Delivery request not found"));

        // تطبيق الخصم إذا وجد
        if (discountCode != null && !discountCode.isEmpty()) {
            Discount discount = discountRepository.findByCodeAndClientId(discountCode, payment.getClientId())
                    .orElseThrow(() -> new RuntimeException("Invalid discount code"));

            if (discount.isUsed()) {
                throw new RuntimeException("Discount code already used");
            }

            double discountAmount = payment.getAmount() * (discount.getPercentage() / 100);
            payment.setDiscountAmount(discountAmount);
            payment.setFinalAmountAfterDiscount(payment.getAmount() - discountAmount);
            payment.setDiscountId(discount.getId());

            // تحديث حالة الخصم
            discount.setUsed(true);
            discountRepository.save(discount);
        } else {
            payment.setFinalAmountAfterDiscount(payment.getAmount());
        }

        // هنا ندمج مع بوابة الدفع (سنشرحها لاحقاً)
        boolean paymentSuccess = processWithPaymentGateway(payment);

        if (paymentSuccess) {
            payment.setStatus(PaymentStatus.PAID);
            delivery.setPaymentStatus(PaymentStatus.PAID);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            delivery.setPaymentStatus(PaymentStatus.FAILED);
        }

        paymentRepository.save(payment);
        deliveryReqRepository.save(delivery);

        return payment;
    }

    /**
     * @param paymentId
     * @return
     */
    @Override
    public Payment getPaymentById(String paymentId) {
        return null;
    }

    private boolean processWithPaymentGateway(Payment payment) {
        // هذه الدالة ستحتوي على تفاصيل الاتصال ببوابة الدفع
        // مثل Stripe أو PayPal أو بوابة دفع محلية
        // سنشرحها بالتفصيل لاحقاً

        // للإغراض التعليمية، سنفترض أن الدفع نجح
        return true;
    }

    @Override
    public Payment refundPayment(String paymentId, Double amount) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        DeliveryRequest delivery = deliveryReqRepository.findById(payment.getDeliveryId())
                .orElseThrow(() -> new RuntimeException("Delivery request not found"));

        // إذا لم يتم تحديد المبلغ، نسترجع المبلغ كاملاً
        double refundAmount = (amount != null) ? amount : payment.getFinalAmountAfterDiscount();

        // التحقق من أن المبلغ المراد استرجاعه لا يتجاوز المبلغ المدفوع
        if (refundAmount > payment.getFinalAmountAfterDiscount()) {
            throw new RuntimeException("Refund amount exceeds payment amount");
        }

        // هنا ندمج مع بوابة الدفع لاسترجاع المبلغ
        boolean refundSuccess = refundWithPaymentGateway(payment, refundAmount);

        if (refundSuccess) {
            if (refundAmount == payment.getFinalAmountAfterDiscount()) {
                payment.setStatus(PaymentStatus.REFUNDED);
                delivery.setPaymentStatus(PaymentStatus.REFUNDED);
            } else {
                payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
                // حالة الدفع تبقى PAID مع إشارة أن جزءاً منه مسترجع
            }

            paymentRepository.save(payment);
            deliveryReqRepository.save(delivery);
        }

        return payment;
    }

    private boolean refundWithPaymentGateway(Payment payment, double amount) {
        // تفاصيل استرجاع المبلغ من بوابة الدفع
        // للإغراض التعليمية، سنفترض أن العملية نجحت
        return true;
    }
}