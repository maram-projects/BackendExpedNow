package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.config.StripeConfig;
import com.example.ExpedNow.models.Payment;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class StripeService {

    private static final Logger logger = LoggerFactory.getLogger(StripeService.class);

    @Autowired
    private StripeConfig stripeConfig;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeConfig.getApiKey();
    }

    /**
     * Create a payment intent with Stripe
     */
    public PaymentIntent createPaymentIntent(Payment payment) throws StripeException {
        // Validate input payment object
        if (payment == null) {
            throw new IllegalArgumentException("Payment cannot be null");
        }

        // Validate that amount is not null
        if (payment.getAmount() == null) {
            throw new IllegalArgumentException("Payment amount cannot be null");
        }

        // Use safe amount retrieval with default to original amount
        Double amountToCharge = payment.getFinalAmountAfterDiscount() != null ?
                payment.getFinalAmountAfterDiscount() :
                payment.getAmount();

        // Additional null check for amountToCharge (shouldn't happen but for safety)
        if (amountToCharge == null || amountToCharge <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }

        // Normalize currency
        String currency = payment.getCurrency() != null ?
                payment.getCurrency().toLowerCase() :
                "tnd";

        logger.info("Creating payment intent for {} {}. Original amount: {} {}",
                amountToCharge, currency,
                payment.getAmount(), payment.getCurrency());

        // Validate minimum amount in TND BEFORE conversion
        if ("tnd".equals(currency)) {
            double minTnd = 1.56; // $0.50 USD equivalent
            if (amountToCharge < minTnd) {
                String errorMsg = String.format(
                        "Amount too small. Minimum charge is %.2f TND (≈$0.50 USD). Provided: %.2f TND",
                        minTnd, amountToCharge
                );
                logger.warn(errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
        }

        // Convert TND to USD for Stripe
        double convertedAmount = amountToCharge;
        String stripeCurrency = currency;
        double exchangeRate = 1.0; // Default rate for non-TND currencies

        if ("tnd".equals(currency)) {
            exchangeRate = getExchangeRate(); // Get the actual exchange rate
            convertedAmount = amountToCharge * exchangeRate;
            stripeCurrency = "usd";

            // Update payment object with conversion details
            payment.setConvertedAmount(convertedAmount);
            payment.setExchangeRate(exchangeRate);
            payment.setConvertedCurrency(stripeCurrency);

            logger.debug("Converted {} TND to {} USD (rate: {})",
                    amountToCharge, convertedAmount, exchangeRate);
        }

        // Convert to cents and validate USD minimum
        long amountInCents = Math.round(convertedAmount * 100);
        if ("usd".equals(stripeCurrency) && amountInCents < 50) {
            String errorMsg = String.format(
                    "Amount too small. Minimum charge is $0.50 USD. Converted amount: $%.2f USD",
                    convertedAmount
            );
            logger.warn(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        logger.info("Creating PaymentIntent for {} {} ({} cents)",
                convertedAmount, stripeCurrency, amountInCents);

        // Build PaymentIntent parameters
        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(stripeCurrency)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                )
                .putMetadata("original_amount", String.valueOf(amountToCharge))
                .putMetadata("original_currency", payment.getCurrency() != null ? payment.getCurrency() : "tnd");

        // Add conversion metadata if currency was converted
        if ("tnd".equals(currency)) {
            paramsBuilder.putMetadata("converted_amount", String.valueOf(convertedAmount));
            paramsBuilder.putMetadata("exchange_rate", String.valueOf(exchangeRate));
            paramsBuilder.putMetadata("converted_currency", stripeCurrency);
        }

        // Add optional metadata
        addMetadata(paramsBuilder, payment);

        // Create and return PaymentIntent
        try {
            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());
            logger.info("Created PaymentIntent: {} for payment: {} (Original: {} {}, Charged: {} {})",
                    paymentIntent.getId(), payment.getId(),
                    amountToCharge, currency.toUpperCase(),
                    convertedAmount, stripeCurrency.toUpperCase());
            return paymentIntent;
        } catch (StripeException e) {
            logger.error("Stripe API error creating PaymentIntent: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error creating PaymentIntent: {}", e.getMessage(), e);
            throw new RuntimeException("PaymentIntent creation failed", e);
        }
    }

    // Helper method to safely add metadata
    private void addMetadata(PaymentIntentCreateParams.Builder builder, Payment payment) {
        if (payment.getId() != null) {
            builder.putMetadata("payment_id", String.valueOf(payment.getId()));
        }
        if (payment.getClientId() != null) {
            builder.putMetadata("client_id", String.valueOf(payment.getClientId()));
        }
        if (payment.getDeliveryId() != null) {
            builder.putMetadata("delivery_id", String.valueOf(payment.getDeliveryId()));
        }
        if (payment.getDiscountCode() != null) {
            builder.putMetadata("discount_code", payment.getDiscountCode());
        }
    }

    // Could be extended to fetch real-time rates
    private double getExchangeRate() {
        // In production, implement:
        // 1. Cached rate from financial API
        // 2. Fallback to default
        return 0.32;
    }

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
     * Create a refund with currency conversion handling
     */
    public Refund createRefund(String paymentIntentId, Double amount, String originalCurrency) throws StripeException {
        try {
            RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId);

            // If amount is specified, refund that amount (in cents)
            if (amount != null) {
                double refundAmount = amount;

                // Convert TND to USD if necessary (same as payment creation)
                if ("tnd".equalsIgnoreCase(originalCurrency)) {
                    double exchangeRate = getExchangeRate();
                    refundAmount = amount * exchangeRate;
                    logger.debug("Converting refund amount {} TND to {} USD (rate: {})",
                            amount, refundAmount, exchangeRate);
                }

                long amountInCents = Math.round(refundAmount * 100);
                paramsBuilder.setAmount(amountInCents);
                logger.info("Creating partial refund of {} cents (original: {} {}) for PaymentIntent: {}",
                        amountInCents, amount, originalCurrency != null ? originalCurrency : "TND", paymentIntentId);
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
     * Create a refund (overloaded method for backward compatibility)
     */
    public Refund createRefund(String paymentIntentId, Double amount) throws StripeException {
        return createRefund(paymentIntentId, amount, null);
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

    /**
     * Get payment details including conversion information
     */
    public PaymentDetails getPaymentDetails(PaymentIntent paymentIntent) {
        PaymentDetails details = new PaymentDetails();
        details.setPaymentIntentId(paymentIntent.getId());
        details.setStatus(paymentIntent.getStatus());
        details.setAmount(paymentIntent.getAmount() / 100.0); // Convert from cents
        details.setCurrency(paymentIntent.getCurrency().toUpperCase());

        // Extract conversion details from metadata
        if (paymentIntent.getMetadata() != null) {
            String originalAmount = paymentIntent.getMetadata().get("original_amount");
            String originalCurrency = paymentIntent.getMetadata().get("original_currency");
            String exchangeRate = paymentIntent.getMetadata().get("exchange_rate");

            if (originalAmount != null) {
                details.setOriginalAmount(Double.parseDouble(originalAmount));
            }
            if (originalCurrency != null) {
                details.setOriginalCurrency(originalCurrency.toUpperCase());
            }
            if (exchangeRate != null) {
                details.setExchangeRate(Double.parseDouble(exchangeRate));
            }
        }

        return details;
    }

    /**
     * Inner class to hold payment details with conversion information
     */
    public static class PaymentDetails {
        private String paymentIntentId;
        private String status;
        private Double amount;
        private String currency;
        private Double originalAmount;
        private String originalCurrency;
        private Double exchangeRate;

        // Getters and setters
        public String getPaymentIntentId() { return paymentIntentId; }
        public void setPaymentIntentId(String paymentIntentId) { this.paymentIntentId = paymentIntentId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }

        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }

        public Double getOriginalAmount() { return originalAmount; }
        public void setOriginalAmount(Double originalAmount) { this.originalAmount = originalAmount; }

        public String getOriginalCurrency() { return originalCurrency; }
        public void setOriginalCurrency(String originalCurrency) { this.originalCurrency = originalCurrency; }

        public Double getExchangeRate() { return exchangeRate; }
        public void setExchangeRate(Double exchangeRate) { this.exchangeRate = exchangeRate; }

        @Override
        public String toString() {
            return "PaymentDetails{" +
                    "paymentIntentId='" + paymentIntentId + '\'' +
                    ", status='" + status + '\'' +
                    ", amount=" + amount +
                    ", currency='" + currency + '\'' +
                    ", originalAmount=" + originalAmount +
                    ", originalCurrency='" + originalCurrency + '\'' +
                    ", exchangeRate=" + exchangeRate +
                    '}';
        }
    }
}