package com.example.ExpedNow.services.core;// Add these method signatures to your PaymentServiceInterface

import com.example.ExpedNow.models.Payment;
import com.example.ExpedNow.models.enums.PaymentMethod;
import com.example.ExpedNow.models.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Map;

public interface PaymentServiceInterface {

    // Existing methods...
    Payment createPayment(Payment payment);
    Payment processPayment(String paymentId, String discountCode);
    Payment getPaymentById(String paymentId);
    String getPaymentClientSecret(String paymentId);
    Payment refundPayment(String paymentId, Double amount);
    Payment confirmPayment(String transactionId, double amount);
    Payment failPayment(String transactionId);
    Payment updatePaymentStatus(String paymentId, String status);
    Payment cancelPayment(String paymentId);
    boolean isPaymentMethodSupported(PaymentMethod paymentMethod);
    Payment processCardPayment(String paymentId, String discountCode);
    Payment processCashPayment(String paymentId, String discountCode);
    Payment processWalletPayment(String paymentId, String discountCode);
    Payment processBankTransferPayment(String paymentId, String discountCode);

    // MISSING METHODS - Add these to match Angular service calls:

    /**
     * Get all payments with pagination and optional filters
     */
    Page<Payment> getAllPayments(PaymentStatus status, PaymentMethod method,
                                 String clientId, String deliveryId, Pageable pageable);

    /**
     * Get all payments without pagination (simple list)
     */
    List<Payment> getAllPaymentsSimple();

    /**
     * Get payments by client ID
     */
    List<Payment> getPaymentsByClient(String clientId);

    /**
     * Get payments by delivery ID
     */
    List<Payment> getPaymentsByDelivery(String deliveryId);

    /**
     * Get payment statistics
     */
    Map<String, Object> getPaymentStats();
}