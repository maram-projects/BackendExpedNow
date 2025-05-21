package com.example.ExpedNow.services.core;

import com.example.ExpedNow.models.Payment;

public interface PaymentServiceInterface {
    Payment createPayment(Payment payment);
    Payment processPayment(String paymentId, String discountCode);
    Payment getPaymentById(String paymentId);
    Payment refundPayment(String paymentId, Double amount);
}
