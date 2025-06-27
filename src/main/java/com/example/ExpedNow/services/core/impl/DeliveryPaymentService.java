package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.Payment;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.repositories.PaymentRepository;
import com.example.ExpedNow.repositories.UserRepository;
import com.example.ExpedNow.services.core.DeliveryPaymentServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryPaymentService implements DeliveryPaymentServiceInterface {
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void processDeliveryPayment(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        User deliveryPerson = userRepository.findById(payment.getDeliveryPersonId())
                .orElseThrow(() -> new RuntimeException("Delivery person not found"));

        double share = payment.getAmount() * 0.7; // 70% للموصل
        payment.setDeliveryPersonShare(share);

        deliveryPerson.setBalance(deliveryPerson.getBalance() + share);
        userRepository.save(deliveryPerson);

        payment.setDeliveryPersonPaid(true);
        paymentRepository.save(payment);

        System.out.println("تم تحويل " + share + " د.ت للموصل " + deliveryPerson.getId());
    }
}