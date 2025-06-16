package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.config.StripeConfig;
import com.example.ExpedNow.models.Payment;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class StripeService {

    private static final Logger logger = LoggerFactory.getLogger(StripeService.class);

    @Autowired
    private StripeConfig stripeConfig;

    /**
     * Create a payment intent with Stripe
     */
    public PaymentIntent createPaymentIntent(Payment payment) throws StripeException {
        try {
            // Convert amount to cents (Stripe uses cents)
            long amountInCents = Math.round(payment.getFinalAmountAfterDiscount() * 100);

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(stripeConfig.getCurrency()) // Use configured currency
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .putMetadata("payment_id", payment.getId())
                    .putMetadata("delivery_id", payment.getDeliveryId())
                    .putMetadata("client_id", payment.getClientId())
                    .setDescription("Payment for delivery request: " + payment.getDeliveryId())
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);
            logger.info("Created PaymentIntent: {} for amount: {}", paymentIntent.getId(), amountInCents);
            return paymentIntent;
        } catch (StripeException e) {
            logger.error("Error creating PaymentIntent: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Confirm a payment intent
     */
    public PaymentIntent confirmPaymentIntent(String paymentIntentId) throws StripeException {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            PaymentIntent confirmedIntent = paymentIntent.confirm();
            logger.info("Confirmed PaymentIntent: {}", paymentIntentId);
            return confirmedIntent;
        } catch (StripeException e) {
            logger.error("Error confirming PaymentIntent {}: {}", paymentIntentId, e.getMessage());
            throw e;
        }
    }

    /**
     * Retrieve a payment intent
     */
    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        try {
            return PaymentIntent.retrieve(paymentIntentId);
        } catch (StripeException e) {
            logger.error("Error retrieving PaymentIntent {}: {}", paymentIntentId, e.getMessage());
            throw e;
        }
    }

    /**
     * Create a refund
     */
    public Refund createRefund(String paymentIntentId, Double amount) throws StripeException {
        try {
            RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId);

            // If amount is specified, refund that amount (in cents)
            if (amount != null) {
                long amountInCents = Math.round(amount * 100);
                paramsBuilder.setAmount(amountInCents);
                logger.info("Creating partial refund of {} cents for PaymentIntent: {}", amountInCents, paymentIntentId);
            } else {
                logger.info("Creating full refund for PaymentIntent: {}", paymentIntentId);
            }

            Refund refund = Refund.create(paramsBuilder.build());
            logger.info("Created refund: {} for PaymentIntent: {}", refund.getId(), paymentIntentId);
            return refund;
        } catch (StripeException e) {
            logger.error("Error creating refund for PaymentIntent {}: {}", paymentIntentId, e.getMessage());
            throw e;
        }
    }

    /**
     * Check if payment is successful
     */
    public boolean isPaymentSuccessful(PaymentIntent paymentIntent) {
        return "succeeded".equals(paymentIntent.getStatus());
    }

    /**
     * Check if payment requires action (like 3D Secure)
     */
    public boolean requiresAction(PaymentIntent paymentIntent) {
        return "requires_action".equals(paymentIntent.getStatus()) ||
                "requires_source_action".equals(paymentIntent.getStatus());
    }

    /**
     * Check if payment has failed
     */
    public boolean isPaymentFailed(PaymentIntent paymentIntent) {
        return "requires_payment_method".equals(paymentIntent.getStatus()) ||
                "canceled".equals(paymentIntent.getStatus());
    }

    /**
     * Get client secret for frontend
     */
    public String getClientSecret(PaymentIntent paymentIntent) {
        return paymentIntent.getClientSecret();
    }

    /**
     * Cancel a payment intent
     */
    public PaymentIntent cancelPaymentIntent(String paymentIntentId) throws StripeException {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            PaymentIntent canceledIntent = paymentIntent.cancel();
            logger.info("Canceled PaymentIntent: {}", paymentIntentId);
            return canceledIntent;
        } catch (StripeException e) {
            logger.error("Error canceling PaymentIntent {}: {}", paymentIntentId, e.getMessage());
            throw e;
        }
    }
}