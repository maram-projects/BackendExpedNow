package com.example.ExpedNow.controllers;

import com.example.ExpedNow.models.Payment;
import com.example.ExpedNow.services.core.PaymentServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentServiceInterface paymentService;

    // إنشاء عملية دفع جديدة
    @PostMapping
    public ResponseEntity<Payment> createPayment(@RequestBody Payment payment) {
        Payment createdPayment = paymentService.createPayment(payment);
        return ResponseEntity.ok(createdPayment);
    }

    // تنفيذ الدفع
    @PostMapping("/{id}/process")
    public ResponseEntity<Payment> processPayment(
            @PathVariable String id,
            @RequestParam(required = false) String discountCode) {
        Payment processedPayment = paymentService.processPayment(id, discountCode);
        return ResponseEntity.ok(processedPayment);
    }

    // الحصول على تفاصيل الدفع
    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPayment(@PathVariable String id) {
        Payment payment = paymentService.getPaymentById(id);
        return ResponseEntity.ok(payment);
    }

    // استرجاع المبلغ (للأدمن)
    @PostMapping("/{id}/refund")
    public ResponseEntity<Payment> refundPayment(
            @PathVariable String id,
            @RequestParam(required = false) Double amount) {
        Payment refundedPayment = paymentService.refundPayment(id, amount);
        return ResponseEntity.ok(refundedPayment);
    }
}