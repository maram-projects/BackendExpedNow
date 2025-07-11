package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.*;
import com.example.ExpedNow.models.enums.PaymentMethod;
import com.example.ExpedNow.models.enums.PaymentStatus;
import com.example.ExpedNow.repositories.PaymentRepository;
import com.example.ExpedNow.repositories.VehicleRepository;
import com.example.ExpedNow.services.core.PaymentServiceInterface;
import com.example.ExpedNow.services.core.DeliveryServiceInterface;
import com.example.ExpedNow.services.core.UserServiceInterface;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PaymentServiceImpl implements PaymentServiceInterface {
    public static final double DELIVERY_PERSON_SHARE_PERCENTAGE = 0.8;

    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);
    @Autowired
    private UserServiceInterface userService; // Add this line
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private VehicleRepository vehicleRepository; // Add this if not already present
    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private StripeService stripeService;

    @Autowired
    private DiscountService discountService; // Assuming you have this service

    @Autowired
    private DeliveryServiceInterface deliveryService; // Added missing dependency

    @Override
    public Payment createPayment(Payment payment) {
        try {
            // Validation
            if (payment == null) {
                throw new IllegalArgumentException("Payment cannot be null");
            }
            if (payment.getAmount() == null || payment.getAmount() <= 0) {
                throw new IllegalArgumentException("Payment amount must be positive");
            }
            if (payment.getPaymentMethod() == null) {
                throw new IllegalArgumentException("Payment method is required");
            }
            if (payment.getDeliveryId() != null) {
                DeliveryRequest delivery = deliveryService.getDeliveryById(payment.getDeliveryId());
                payment.setDeliveryPersonId(delivery.getDeliveryPersonId());
            }

            // Set defaults
            payment.setCreatedAt(LocalDateTime.now());
            payment.setStatus(PaymentStatus.PENDING);
            if (payment.getFinalAmountAfterDiscount() == null) {
                payment.setFinalAmountAfterDiscount(payment.getAmount());
            }
            if (payment.getFinalAmountAfterDiscount() <= 0) {
                throw new IllegalArgumentException("Final payment amount must be positive");
            }

            // Initial save
            Payment savedPayment = paymentRepository.save(payment);

            // Handle credit card payments
            if (payment.getPaymentMethod() == PaymentMethod.CREDIT_CARD) {
                try {
                    logger.info("Creating Stripe payment intent for payment: {}", savedPayment.getId());

                    // Create Stripe payment intent
                    PaymentIntent paymentIntent = stripeService.createPaymentIntent(savedPayment);

                    // Update payment with Stripe details
                    savedPayment.setTransactionId(paymentIntent.getId());
                    savedPayment.setClientSecret(paymentIntent.getClientSecret());

                    // Save conversion details
                    if (paymentIntent.getMetadata() != null) {
                        String convertedAmount = paymentIntent.getMetadata().get("converted_amount");
                        String exchangeRate = paymentIntent.getMetadata().get("exchange_rate");
                        String convertedCurrency = paymentIntent.getMetadata().get("converted_currency");

                        if (convertedAmount != null) {
                            savedPayment.setConvertedAmount(Double.parseDouble(convertedAmount));
                        }
                        if (exchangeRate != null) {
                            savedPayment.setExchangeRate(Double.parseDouble(exchangeRate));
                        }
                        if (convertedCurrency != null) {
                            savedPayment.setConvertedCurrency(convertedCurrency);
                        }
                    }

                    logger.info("Stripe PaymentIntent created: {}", paymentIntent.getId());
                    return paymentRepository.save(savedPayment);
                } catch (StripeException e) {
                    logger.error("Stripe error creating PaymentIntent: {}", e.getMessage(), e);
                    paymentRepository.deleteById(savedPayment.getId());
                    throw new RuntimeException("Payment processing failed: " + e.getMessage(), e);
                } catch (Exception e) {
                    logger.error("Unexpected error during Stripe processing: {}", e.getMessage(), e);
                    paymentRepository.deleteById(savedPayment.getId());
                    throw new RuntimeException("Payment processing failed", e);
                }
            }

            // Return non-card payments
            logger.info("Created non-card payment: {}", savedPayment.getId());
            return savedPayment;
        } catch (IllegalArgumentException e) {
            logger.error("Validation error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            throw new RuntimeException("Payment creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Payment processPayment(String paymentId, String discountCode) {
        try {
            Payment payment = getPaymentById(paymentId);

            // Apply discount if provided
            if (discountCode != null && !discountCode.isEmpty()) {
                payment = applyDiscount(payment, discountCode);
                payment = paymentRepository.save(payment);
            }

            // Process based on payment method
            switch (payment.getPaymentMethod()) {
                case CREDIT_CARD:
                    return processCardPayment(paymentId, discountCode);
                case WALLET:
                    return processWalletPayment(paymentId, discountCode);
                case BANK_TRANSFER:
                    return processBankTransferPayment(paymentId, discountCode);
                default:
                    throw new IllegalArgumentException("Unsupported payment method: " + payment.getPaymentMethod());
            }
        } catch (Exception e) {
            logger.error("Error processing payment {}: {}", paymentId, e.getMessage());
            throw new RuntimeException("Failed to process payment: " + e.getMessage());
        }
    }

    // Update methods to use the enricher
    @Override
    public Payment getPaymentById(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        return enrichPayment(payment);
    }

    @Override
    public String getPaymentClientSecret(String paymentId) {
        Payment payment = getPaymentById(paymentId);

        if (payment.getPaymentMethod() == PaymentMethod.CREDIT_CARD) {
            if (payment.getClientSecret() != null) {
                return payment.getClientSecret();
            } else if (payment.getTransactionId() != null) {
                try {
                    PaymentIntent paymentIntent = stripeService.retrievePaymentIntent(payment.getTransactionId());
                    return paymentIntent.getClientSecret();
                } catch (StripeException e) {
                    logger.error("Error retrieving client secret for payment {}: {}", paymentId, e.getMessage());
                    throw new RuntimeException("Failed to get client secret: " + e.getMessage());
                }
            }
        }

        throw new IllegalArgumentException("Client secret not available for this payment method");
    }

    // Add these methods to PaymentServiceImpl

    @Override
    public List<Payment> getPaymentsByDeliveryPerson(String deliveryPersonId) {
        try {
            if (deliveryPersonId == null || deliveryPersonId.trim().isEmpty()) {
                throw new IllegalArgumentException("Delivery person ID cannot be null or empty");
            }

            Query query = new Query();
            query.addCriteria(Criteria.where("deliveryPersonId").is(deliveryPersonId)
                    .and("deliveryPersonPaid").is(true));

            // Sort by payment date descending
            query.with(Sort.by(Sort.Direction.DESC, "deliveryPersonPaidAt"));

            return mongoTemplate.find(query, Payment.class);
        } catch (Exception e) {
            logger.error("Error getting payments for delivery person {}: {}", deliveryPersonId, e.getMessage());
            throw new RuntimeException("Failed to retrieve payments for delivery person", e);
        }
    }



    @Override
    public ResponseEntity<?> releaseToDeliveryPerson(String paymentId) {
        try {
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new IllegalArgumentException("Payment not found with ID: " + paymentId));

            if (payment.getStatus() != PaymentStatus.COMPLETED) {
                throw new IllegalArgumentException(
                        "Payment status must be COMPLETED to release. Current status: " + payment.getStatus()
                );
            }

            if (payment.getDeliveryPersonId() == null) {
                throw new IllegalArgumentException(
                        "Cannot release payment - no delivery person assigned. Payment ID: " + paymentId
                );
            }

            if (payment.getDeliveryPersonPaid() != null && payment.getDeliveryPersonPaid()) {
                throw new IllegalArgumentException(
                        "Payment already released to delivery person at: " + payment.getDeliveryPersonPaidAt()
                );
            }

            // Calculate 80% share
            double shareAmount = payment.getFinalAmountAfterDiscount() * DELIVERY_PERSON_SHARE_PERCENTAGE;
            payment.setDeliveryPersonShare(shareAmount);
            payment.setDeliveryPersonPaid(true);
            payment.setDeliveryPersonPaidAt(LocalDateTime.now());

            paymentRepository.save(payment);

            // Return response with payment details
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment released to delivery person successfully");
            response.put("paymentId", payment.getId());
            response.put("deliveryPersonId", payment.getDeliveryPersonId());
            response.put("amountReleased", shareAmount);
            response.put("releaseDate", payment.getDeliveryPersonPaidAt());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            logger.error("Error releasing payment: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Failed to release payment: " + e.getMessage()
            ));
        }
    }
    @Override
    public Payment refundPayment(String paymentId, Double amount) {
        try {
            Payment payment = getPaymentById(paymentId);

            if (payment.getStatus() != PaymentStatus.COMPLETED) {
                throw new IllegalArgumentException("Cannot refund payment that is not completed");
            }

            if (payment.getPaymentMethod() == PaymentMethod.CREDIT_CARD &&
                    payment.getTransactionId() != null) {

                // Process Stripe refund with currency conversion
                try {
                    // Get original currency (default to TND if null)
                    String originalCurrency = payment.getCurrency() != null ?
                            payment.getCurrency() : "TND";

                    // Process refund through Stripe
                    stripeService.createRefund(
                            payment.getTransactionId(),
                            amount,
                            originalCurrency
                    );

                    // Update payment status
                    if (amount == null || amount >= payment.getFinalAmountAfterDiscount()) {
                        payment.setStatus(PaymentStatus.REFUNDED);
                    } else {
                        payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
                    }

                    payment.setUpdatedAt(LocalDateTime.now());
                    return paymentRepository.save(payment);
                } catch (StripeException e) {
                    logger.error("Stripe refund error: {}", e.getMessage());
                    throw new RuntimeException("Failed to create refund: " + e.getMessage());
                }
            } else {
                // Handle non-card payments
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.setUpdatedAt(LocalDateTime.now());
                return paymentRepository.save(payment);
            }
        } catch (Exception e) {
            logger.error("Refund error: {}", e.getMessage());
            throw new RuntimeException("Failed to refund payment: " + e.getMessage());
        }
    }

    @Override
    public Payment confirmPayment(String transactionId, double amount) {
        // Input validation
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        try {
            // Retrieve payment intent
            PaymentIntent paymentIntent = stripeService.retrievePaymentIntent(transactionId);
            if (paymentIntent == null) {
                throw new IllegalStateException("PaymentIntent not found");
            }

            // Extract metadata
            Map<String, String> metadata = paymentIntent.getMetadata();
            String paymentId = metadata != null ? metadata.get("payment_id") : null;
            String deliveryIdFromMetadata = metadata != null ? metadata.get("delivery_id") : null;
            String clientIdFromMetadata = metadata != null ? metadata.get("client_id") : null;

            // Try to find existing payment
            Payment payment = findExistingPayment(transactionId, paymentId);

            if (payment == null) {
                // Create new payment from Stripe data
                payment = createPaymentFromStripeData(transactionId, paymentIntent, amount,
                        clientIdFromMetadata, deliveryIdFromMetadata);
                logger.info("Created new payment from Stripe data: {}", payment.getId());
            } else {
                // Update existing payment
                updateExistingPayment(payment, amount);
                logger.info("Updated existing payment: {}", payment.getId());
            }

            // Save payment
            Payment savedPayment = paymentRepository.save(payment);

            // Update delivery status
            updateDeliveryStatusIfNeeded(savedPayment, deliveryIdFromMetadata);

            logger.info("Payment confirmed: {}", transactionId);
            return savedPayment;

        } catch (StripeException e) {
            logger.error("Stripe confirmation error: {}", e.getMessage(), e);
            throw new RuntimeException("Stripe error: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Confirmation error: {}", e.getMessage(), e);
            throw new RuntimeException("Confirmation failed: " + e.getMessage(), e);
        }
    }

    private Payment createPaymentFromStripeData(String transactionId, PaymentIntent paymentIntent, double amount,
                                                String clientId, String deliveryId) {
        Payment newPayment = new Payment();
        newPayment.setTransactionId(transactionId);

        // Extract conversion details from metadata
        Map<String, String> metadata = paymentIntent.getMetadata();
        if (metadata != null) {
            if (metadata.containsKey("original_amount")) {
                double originalAmount = Double.parseDouble(metadata.get("original_amount"));
                newPayment.setAmount(originalAmount);
                newPayment.setFinalAmountAfterDiscount(originalAmount);
            }
            if (metadata.containsKey("original_currency")) {
                newPayment.setCurrency(metadata.get("original_currency"));
            }
            if (metadata.containsKey("converted_amount")) {
                newPayment.setConvertedAmount(Double.parseDouble(metadata.get("converted_amount")));
            }
            if (metadata.containsKey("exchange_rate")) {
                newPayment.setExchangeRate(Double.parseDouble(metadata.get("exchange_rate")));
            }
            if (metadata.containsKey("converted_currency")) {
                newPayment.setConvertedCurrency(metadata.get("converted_currency"));
            }
        }

        // Set default values if metadata missing
        if (newPayment.getAmount() == null) {
            newPayment.setAmount(amount);
            newPayment.setFinalAmountAfterDiscount(amount);
        }
        if (newPayment.getCurrency() == null) {
            newPayment.setCurrency("usd");
        }

        // Set other fields
        newPayment.setStatus(PaymentStatus.COMPLETED);
        newPayment.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        newPayment.setPaymentDate(LocalDateTime.now());
        newPayment.setCreatedAt(LocalDateTime.now());
        newPayment.setUpdatedAt(LocalDateTime.now());

        if (clientId != null && !clientId.trim().isEmpty()) {
            newPayment.setClientId(clientId);
        }
        if (deliveryId != null && !deliveryId.trim().isEmpty()) {
            newPayment.setDeliveryId(deliveryId);
        }

        return newPayment;
    }



    /**
     * Updates delivery status if delivery ID is available
     */
    private void updateDeliveryStatusIfNeeded(Payment payment, String deliveryIdFromMetadata) {
        try {
            String deliveryId = payment.getDeliveryId();

            // Use delivery ID from metadata if payment doesn't have one
            if ((deliveryId == null || deliveryId.trim().isEmpty()) &&
                    deliveryIdFromMetadata != null && !deliveryIdFromMetadata.trim().isEmpty()) {
                deliveryId = deliveryIdFromMetadata;
                payment.setDeliveryId(deliveryId); // Update payment with delivery ID
            }

            if (deliveryId != null && !deliveryId.trim().isEmpty()) {
                deliveryService.updateDeliveryPaymentStatus(
                        deliveryId,
                        payment.getId(),
                        PaymentStatus.COMPLETED
                );
                logger.info("Updated delivery status for delivery: {}", deliveryId);
            }

            if (deliveryId != null) {
                DeliveryRequest delivery = deliveryService.getDeliveryById(deliveryId);
                if (delivery != null) {
                    payment.setDeliveryPersonId(delivery.getDeliveryPersonId()); // Add this
                    deliveryService.updateDeliveryPaymentStatus(
                            deliveryId,
                            payment.getId(),
                            PaymentStatus.COMPLETED
                    );
                }
            }
        } catch (Exception e) {
            // Log error but don't fail the payment confirmation
            logger.error("Failed to update delivery status for payment {}: {}",
                    payment.getId(), e.getMessage(), e);
        }
    }
    /**
     * Updates an existing payment with confirmation data
     */
    private void updateExistingPayment(Payment payment, double stripeAmount) {
        // Only update status, not amount
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaymentDate(LocalDateTime.now());
        }
        payment.setUpdatedAt(LocalDateTime.now());


        // DO NOT update amount from Stripe
    }


    /**
     * Attempts to find an existing payment by transaction ID first, then by payment ID
     */
    private Payment findExistingPayment(String transactionId, String paymentId) {
        // First try to find by transactionId
        Optional<Payment> paymentOpt = paymentRepository.findByTransactionId(transactionId);

        if (paymentOpt.isPresent()) {
            return paymentOpt.get();
        }

        // If not found and we have a payment ID from metadata, try that
        if (paymentId != null && !paymentId.trim().isEmpty()) {
            paymentOpt = paymentRepository.findById(paymentId);
            if (paymentOpt.isPresent()) {
                return paymentOpt.get();
            }
        }

        return null; // No existing payment found
    }


    @Override
    public Payment confirmPaymentByIntent(String paymentIntentId, double amount) {
        // This method is similar to confirmPayment but specifically for PaymentIntent IDs
        return confirmPayment(paymentIntentId, amount);
    }

    @Override
    public Payment failPayment(String transactionId) {
        try {
            Optional<Payment> paymentOpt = paymentRepository.findByTransactionId(transactionId);
            if (paymentOpt.isPresent()) {
                Payment payment = paymentOpt.get();
                payment.setStatus(PaymentStatus.FAILED);
                payment.setUpdatedAt(LocalDateTime.now());

                Payment savedPayment = paymentRepository.save(payment);
                logger.info("Failed payment: {}", transactionId);
                return savedPayment;
            } else {
                logger.warn("Payment not found for transaction ID: {}", transactionId);
                throw new IllegalArgumentException("Payment not found for transaction ID: " + transactionId);
            }
        } catch (Exception e) {
            logger.error("Error failing payment {}: {}", transactionId, e.getMessage());
            throw new RuntimeException("Failed to fail payment: " + e.getMessage());
        }
    }

    @Override
    public Payment updatePaymentStatus(String paymentId, String status) {
        try {
            Payment payment = getPaymentById(paymentId);
            payment.setStatus(PaymentStatus.valueOf(status.toUpperCase()));
            payment.setUpdatedAt(LocalDateTime.now());

            return paymentRepository.save(payment);
        } catch (Exception e) {
            logger.error("Error updating payment status for {}: {}", paymentId, e.getMessage());
            throw new RuntimeException("Failed to update payment status: " + e.getMessage());
        }
    }

    @Override
    public Payment cancelPayment(String paymentId) {
        try {
            Payment payment = getPaymentById(paymentId);

            if (payment.getPaymentMethod() == PaymentMethod.CREDIT_CARD && payment.getTransactionId() != null) {
                try {
                    stripeService.cancelPaymentIntent(payment.getTransactionId());
                } catch (StripeException e) {
                    logger.error("Error canceling Stripe PaymentIntent {}: {}", payment.getTransactionId(), e.getMessage());
                    // Continue with local cancellation even if Stripe fails
                }
            }

            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setUpdatedAt(LocalDateTime.now());

            return paymentRepository.save(payment);
        } catch (Exception e) {
            logger.error("Error canceling payment {}: {}", paymentId, e.getMessage());
            throw new RuntimeException("Failed to cancel payment: " + e.getMessage());
        }
    }

    @Override
    public boolean isPaymentMethodSupported(PaymentMethod paymentMethod) {
        // Define which payment methods are supported
        return paymentMethod == PaymentMethod.CREDIT_CARD ||
                paymentMethod == PaymentMethod.WALLET ||
                paymentMethod == PaymentMethod.BANK_TRANSFER;
    }

    @Override
    public Payment processCardPayment(String paymentId, String discountCode) {
        // Card payments are processed through Stripe webhooks
        // This method can be used to update local status
        Payment payment = getPaymentById(paymentId);
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setUpdatedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    @Override
    public Payment processCashPayment(String paymentId, String discountCode) {
        Payment payment = getPaymentById(paymentId);
        // Cash payments are completed when delivery is made
        payment.setStatus(PaymentStatus.PENDING_DELIVERY);
        payment.setUpdatedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    @Override
    public Payment processWalletPayment(String paymentId, String discountCode) {
        Payment payment = getPaymentById(paymentId);

        // TODO: Implement actual wallet payment logic here
        // This might involve:
        // 1. Check wallet balance
        // 2. Deduct amount from wallet
        // 3. Create transaction record

        // For now, simulate wallet payment processing
        try {
            // Simulate wallet balance check and deduction
            // You would integrate with your wallet service here

            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setUpdatedAt(LocalDateTime.now());

            logger.info("Wallet payment processed successfully for payment: {}", paymentId);

        } catch (Exception e) {
            logger.error("Wallet payment failed for payment {}: {}", paymentId, e.getMessage());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setUpdatedAt(LocalDateTime.now());
        }

        return paymentRepository.save(payment);
    }

    @Override
    public Payment processBankTransferPayment(String paymentId, String discountCode) {
        Payment payment = getPaymentById(paymentId);
        // Bank transfers require manual verification
        payment.setStatus(PaymentStatus.PENDING_VERIFICATION);
        payment.setUpdatedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    // NEW METHODS FOR ANGULAR SERVICE COMPATIBILITY

    @Override
    public Page<Payment> getAllPayments(PaymentStatus status, PaymentMethod method,
                                        String clientId, String deliveryId, Pageable pageable) {
        try {
            // Create MongoDB query with criteria
            Query query = new Query();

            if (status != null) {
                query.addCriteria(Criteria.where("status").is(status));
            }
            if (method != null) {
                query.addCriteria(Criteria.where("method").is(method));
            }
            if (clientId != null && !clientId.isEmpty()) {
                query.addCriteria(Criteria.where("clientId").is(clientId));
            }
            if (deliveryId != null && !deliveryId.isEmpty()) {
                query.addCriteria(Criteria.where("deliveryId").is(deliveryId));
            }

            // Get total count
            long total = mongoTemplate.count(query, Payment.class);

            // Apply pagination
            query.with(pageable);

            // Execute query
            List<Payment> payments = mongoTemplate.find(query, Payment.class);

            return new PageImpl<>(payments, pageable, total);
        } catch (Exception e) {
            logger.error("Error getting paginated payments: {}", e.getMessage());
            throw new RuntimeException("Failed to retrieve payments: " + e.getMessage());
        }
    }
    private void populateDeliveryPerson(Payment payment) {
        if (payment.getDeliveryPersonId() != null && payment.getDeliveryPerson() == null) {
            try {
                User deliveryPerson = userService.findById(payment.getDeliveryPersonId());
                payment.setDeliveryPerson(deliveryPerson);

                // Populate vehicle if available
                if (deliveryPerson.getAssignedVehicleId() != null) {
                    Vehicle vehicle = vehicleRepository.findById(deliveryPerson.getAssignedVehicleId())
                            .orElse(null);
                    deliveryPerson.setAssignedVehicle(vehicle);
                }
            } catch (Exception e) {
                logger.error("Error populating delivery person: {}", e.getMessage());
            }
        }
    }

    private Payment enrichPayment(Payment payment) {
        populateDeliveryPerson(payment);
        // Add other population logic if needed
        return payment;
    }

    @Override
    public List<Payment> getAllPaymentsSimple() {
        List<Payment> payments = paymentRepository.findAll();
        return payments.stream()
                .map(this::enrichPayment)
                .collect(Collectors.toList());
    }

    @Override
    public List<Payment> getPaymentsByClient(String clientId) {
        logger.info("PaymentService.getPaymentsByClient called with clientId: {}", clientId);

        try {
            if (clientId == null || clientId.trim().isEmpty()) {
                throw new IllegalArgumentException("Client ID cannot be null or empty");
            }

            // Make sure your repository method exists and works
            List<Payment> payments = paymentRepository.findByClientId(clientId);
            logger.info("Found {} payments for client {}", payments.size(), clientId);

            return payments != null ? payments : new ArrayList<>();

        } catch (Exception e) {
            logger.error("Error in PaymentService.getPaymentsByClient: {}", e.getMessage(), e);
            throw e; // Re-throw to let controller handle it
        }
    }

    @Override
    public List<Payment> getPaymentsByDelivery(String deliveryId) {
        try {
            if (deliveryId == null || deliveryId.isEmpty()) {
                throw new IllegalArgumentException("Delivery ID cannot be null or empty");
            }
            return paymentRepository.findByDeliveryId(deliveryId);
        } catch (Exception e) {
            logger.error("Error getting payments for delivery {}: {}", deliveryId, e.getMessage());
            throw new RuntimeException("Failed to retrieve payments for delivery: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getPaymentStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // Total payments count
            long totalPayments = paymentRepository.count();
            stats.put("totalPayments", totalPayments);

            // Total revenue (sum of completed payments) - Using MongoDB aggregation
            Query completedQuery = new Query(Criteria.where("status").is(PaymentStatus.COMPLETED));
            List<Payment> completedPayments = mongoTemplate.find(completedQuery, Payment.class);
            Double totalRevenue = completedPayments.stream()
                    .mapToDouble(Payment::getFinalAmountAfterDiscount)
                    .sum();
            stats.put("totalRevenue", totalRevenue);

            // Status breakdown - Using MongoDB aggregation
            Map<String, Long> statusBreakdown = new HashMap<>();
            for (PaymentStatus status : PaymentStatus.values()) {
                Query statusQuery = new Query(Criteria.where("status").is(status));
                long count = mongoTemplate.count(statusQuery, Payment.class);
                statusBreakdown.put(status.name(), count);
            }
            stats.put("statusBreakdown", statusBreakdown);

            // Method breakdown - Using MongoDB aggregation
            Map<String, Long> methodBreakdown = new HashMap<>();
            for (PaymentMethod method : PaymentMethod.values()) {
                Query methodQuery = new Query(Criteria.where("method").is(method));
                long count = mongoTemplate.count(methodQuery, Payment.class);
                methodBreakdown.put(method.name(), count);
            }
            stats.put("methodBreakdown", methodBreakdown);

            logger.info("Payment statistics generated successfully");
            return stats;
        } catch (Exception e) {
            logger.error("Error getting payment statistics: {}", e.getMessage());
            throw new RuntimeException("Failed to retrieve payment statistics: " + e.getMessage());
        }
    }

    // PRIVATE HELPER METHODS

    private Payment applyDiscount(Payment payment, String discountCode) {
        try {
            if (discountService != null) {
                // Validate the discount first
                Discount discount = discountService.validateDiscount(discountCode, payment.getClientId());

                // Calculate discount amount based on discount type
                double discountAmount = 0;
                if (discount.getType() == com.example.ExpedNow.models.enums.DiscountType.PERCENTAGE) {
                    discountAmount = payment.getAmount() * (discount.getPercentage() / 100.0);
                    // Ensure discount doesn't exceed maximum allowed
                    if (discount.getMaxAmount() != null && discountAmount > discount.getMaxAmount()) {
                        discountAmount = discount.getMaxAmount();
                    }
                } else if (discount.getType() == com.example.ExpedNow.models.enums.DiscountType.FIXED_AMOUNT) {
                    discountAmount = discount.getFixedAmount();
                }

                // Ensure discount doesn't exceed payment amount
                if (discountAmount > payment.getAmount()) {
                    discountAmount = payment.getAmount();
                }

                // Apply discount
                payment.setDiscountAmount(discountAmount);
                payment.setFinalAmountAfterDiscount(payment.getAmount() - discountAmount);
                payment.setDiscountCode(discountCode);
                payment.setDiscountId(discount.getId());

                logger.info("Discount {} applied to payment {}: {} discount",
                        discountCode, payment.getId(), discountAmount);
            }
            return payment;
        } catch (Exception e) {
            logger.warn("Error applying discount code {}: {}", discountCode, e.getMessage());
            // Continue without discount if there's an error
            payment.setFinalAmountAfterDiscount(payment.getAmount());
            return payment;
        }
    }



}