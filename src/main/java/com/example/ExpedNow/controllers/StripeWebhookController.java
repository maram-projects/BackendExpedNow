package com.example.ExpedNow.controllers;

import com.example.ExpedNow.config.StripeConfig;
import com.example.ExpedNow.services.core.PaymentServiceInterface;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stripe")
public class StripeWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

    @Autowired
    private StripeConfig stripeConfig;

    @Autowired
    private PaymentServiceInterface paymentService;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhook().getSecret());
            logger.info("Received Stripe webhook event: {}", event.getType());
        } catch (SignatureVerificationException e) {
            logger.error("Invalid Stripe webhook signature: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            logger.error("Error processing Stripe webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook error");
        }

        // Handle the event
        try {
            switch (event.getType()) {
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;
                case "payment_intent.payment_failed":
                    handlePaymentIntentFailed(event);
                    break;
                case "payment_intent.requires_action":
                    handlePaymentIntentRequiresAction(event);
                    break;
                case "payment_intent.canceled":
                    handlePaymentIntentCanceled(event);
                    break;
                default:
                    logger.info("Unhandled event type: {}", event.getType());
            }
        } catch (Exception e) {
            logger.error("Error handling webhook event {}: {}", event.getType(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Event processing error");
        }

        return ResponseEntity.ok("Success");
    }

    @PostConstruct
    public void validateConfig() {
        if (stripeConfig == null || stripeConfig.getWebhook() == null ||
                stripeConfig.getWebhook().getSecret() == null ||
                stripeConfig.getWebhook().getSecret().isEmpty()) {
            throw new IllegalStateException("Stripe webhook secret not configured properly");
        }
        logger.info("Stripe webhook configuration validated successfully");
    }

    private void handlePaymentIntentSucceeded(Event event) {
        try {
            StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
            if (stripeObject instanceof PaymentIntent) {
                PaymentIntent paymentIntent = (PaymentIntent) stripeObject;

                // Convert from cents to dollars
                double amount = paymentIntent.getAmount() / 100.0;

                // Get metadata for additional context
                String paymentId = paymentIntent.getMetadata().get("payment_id");
                String deliveryId = paymentIntent.getMetadata().get("delivery_id");

                // Confirm payment in our system
                paymentService.confirmPayment(paymentIntent.getId(), amount);

                logger.info("Payment succeeded - PaymentIntent: {}, Amount: ${}, PaymentId: {}, DeliveryId: {}",
                        paymentIntent.getId(), amount, paymentId, deliveryId);
            } else {
                logger.warn("Received payment_intent.succeeded event but object is not PaymentIntent");
            }
        } catch (Exception e) {
            logger.error("Error handling payment success for event {}: {}", event.getId(), e.getMessage());
            // Don't rethrow - we don't want to cause webhook retries for our internal errors
        }
    }

    private void handlePaymentIntentFailed(Event event) {
        try {
            StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
            if (stripeObject instanceof PaymentIntent) {
                PaymentIntent paymentIntent = (PaymentIntent) stripeObject;

                String paymentId = paymentIntent.getMetadata().get("payment_id");
                String deliveryId = paymentIntent.getMetadata().get("delivery_id");

                // Mark payment as failed in our system
                paymentService.failPayment(paymentIntent.getId());

                logger.warn("Payment failed - PaymentIntent: {}, PaymentId: {}, DeliveryId: {}",
                        paymentIntent.getId(), paymentId, deliveryId);
            } else {
                logger.warn("Received payment_intent.payment_failed event but object is not PaymentIntent");
            }
        } catch (Exception e) {
            logger.error("Error handling payment failure for event {}: {}", event.getId(), e.getMessage());
        }
    }

    private void handlePaymentIntentRequiresAction(Event event) {
        try {
            StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
            if (stripeObject instanceof PaymentIntent) {
                PaymentIntent paymentIntent = (PaymentIntent) stripeObject;

                String paymentId = paymentIntent.getMetadata().get("payment_id");
                String deliveryId = paymentIntent.getMetadata().get("delivery_id");

                logger.info("Payment requires action - PaymentIntent: {}, PaymentId: {}, DeliveryId: {}",
                        paymentIntent.getId(), paymentId, deliveryId);

                // You might want to notify the user or update the payment status in your database
                // paymentService.updatePaymentStatus(paymentIntent.getId(), "requires_action");
            } else {
                logger.warn("Received payment_intent.requires_action event but object is not PaymentIntent");
            }
        } catch (Exception e) {
            logger.error("Error handling payment requires action for event {}: {}", event.getId(), e.getMessage());
        }
    }

    private void handlePaymentIntentCanceled(Event event) {
        try {
            StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
            if (stripeObject instanceof PaymentIntent) {
                PaymentIntent paymentIntent = (PaymentIntent) stripeObject;

                String paymentId = paymentIntent.getMetadata().get("payment_id");
                String deliveryId = paymentIntent.getMetadata().get("delivery_id");

                // Mark payment as canceled in our system
                // paymentService.cancelPayment(paymentIntent.getId());

                logger.info("Payment canceled - PaymentIntent: {}, PaymentId: {}, DeliveryId: {}",
                        paymentIntent.getId(), paymentId, deliveryId);
            } else {
                logger.warn("Received payment_intent.canceled event but object is not PaymentIntent");
            }
        } catch (Exception e) {
            logger.error("Error handling payment cancellation for event {}: {}", event.getId(), e.getMessage());
        }
    }
}