package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.Discount;
import com.example.ExpedNow.models.Payment;
import com.example.ExpedNow.models.enums.PaymentMethod;
import com.example.ExpedNow.models.enums.PaymentStatus;
import com.example.ExpedNow.repositories.PaymentRepository;
import com.example.ExpedNow.services.core.PaymentServiceInterface;
import com.example.ExpedNow.services.core.DeliveryServiceInterface;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentServiceImpl implements PaymentServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);

    @Autowired
    private PaymentRepository paymentRepository;

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
            // Set initial values
            payment.setCreatedAt(LocalDateTime.now());
            payment.setStatus(PaymentStatus.PENDING);

            // Save payment first to get ID
            Payment savedPayment = paymentRepository.save(payment);

            // Create Stripe PaymentIntent for card payments
            if (payment.getPaymentMethod() == PaymentMethod.CREDIT_CARD) {
                try {
                    PaymentIntent paymentIntent = stripeService.createPaymentIntent(savedPayment);
                    savedPayment.setTransactionId(paymentIntent.getId());
                    savedPayment.setClientSecret(paymentIntent.getClientSecret());
                    savedPayment = paymentRepository.save(savedPayment);
                } catch (StripeException e) {
                    logger.error("Error creating Stripe PaymentIntent", e);
                    // Don't fail - we can try again later
                }
            }

            return savedPayment;
        } catch (Exception e) {
            logger.error("Error creating payment", e);
            throw new RuntimeException("Failed to create payment: " + e.getMessage());
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

    @Override
    public Payment getPaymentById(String paymentId) {
        Optional<Payment> payment = paymentRepository.findById(paymentId);
        if (payment.isPresent()) {
            return payment.get();
        } else {
            throw new IllegalArgumentException("Payment not found with ID: " + paymentId);
        }
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

    @Override
    public Payment refundPayment(String paymentId, Double amount) {
        try {
            Payment payment = getPaymentById(paymentId);

            if (payment.getStatus() != PaymentStatus.COMPLETED) {
                throw new IllegalArgumentException("Cannot refund payment that is not completed");
            }

            if (payment.getPaymentMethod() == PaymentMethod.CREDIT_CARD && payment.getTransactionId() != null) {
                // Process Stripe refund
                try {
                    stripeService.createRefund(payment.getTransactionId(), amount);

                    // Update payment status
                    payment.setStatus(amount == null || amount >= payment.getFinalAmountAfterDiscount()
                            ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
                    payment.setUpdatedAt(LocalDateTime.now());

                    return paymentRepository.save(payment);
                } catch (StripeException e) {
                    logger.error("Error creating refund for payment {}: {}", paymentId, e.getMessage());
                    throw new RuntimeException("Failed to create refund: " + e.getMessage());
                }
            } else {
                // Handle refund for non-card payments
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.setUpdatedAt(LocalDateTime.now());
                return paymentRepository.save(payment);
            }
        } catch (Exception e) {
            logger.error("Error refunding payment {}: {}", paymentId, e.getMessage());
            throw new RuntimeException("Failed to refund payment: " + e.getMessage());
        }
    }

    @Override
    public Payment confirmPayment(String transactionId, double amount) {
        if (transactionId == null || transactionId.isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        try {
            // First try to find by transactionId
            Optional<Payment> paymentOpt = paymentRepository.findByTransactionId(transactionId);

            if (paymentOpt.isEmpty()) {
                // If not found, try to find by payment_id in Stripe metadata
                PaymentIntent paymentIntent = stripeService.retrievePaymentIntent(transactionId);
                String paymentId = paymentIntent.getMetadata().get("payment_id");
                String deliveryId = paymentIntent.getMetadata().get("delivery_id");

                if (paymentId != null) {
                    paymentOpt = paymentRepository.findById(paymentId);
                }

                if (paymentOpt.isEmpty()) {
                    // Create new payment record from Stripe data
                    Payment newPayment = new Payment();
                    newPayment.setTransactionId(transactionId);
                    newPayment.setAmount(amount);
                    newPayment.setStatus(PaymentStatus.COMPLETED);
                    newPayment.setPaymentMethod(PaymentMethod.CREDIT_CARD);
                    newPayment.setPaymentDate(LocalDateTime.now());

                    // Set metadata fields if available
                    if (paymentIntent.getMetadata() != null) {
                        newPayment.setClientId(paymentIntent.getMetadata().get("client_id"));
                        newPayment.setDeliveryId(paymentIntent.getMetadata().get("delivery_id"));
                    }

                    Payment savedPayment = paymentRepository.save(newPayment);

                    // Update delivery status if deliveryId exists
                    if (deliveryId != null && !deliveryId.isEmpty()) {
                        deliveryService.updateDeliveryPaymentStatus(
                                deliveryId,
                                savedPayment.getId(),
                                PaymentStatus.COMPLETED
                        );
                    }

                    return savedPayment;
                }
            }

            Payment payment = paymentOpt.get();
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setUpdatedAt(LocalDateTime.now());
            payment.setAmount(amount);
            payment.setPaymentDate(LocalDateTime.now());

            Payment savedPayment = paymentRepository.save(payment);

            // Update delivery status if deliveryId exists
            if (payment.getDeliveryId() != null && !payment.getDeliveryId().isEmpty()) {
                deliveryService.updateDeliveryPaymentStatus(
                        payment.getDeliveryId(),
                        savedPayment.getId(),
                        PaymentStatus.COMPLETED
                );
            }

            return savedPayment;
        } catch (Exception e) {
            logger.error("Error confirming payment", e);
            throw new RuntimeException("Failed to confirm payment: " + e.getMessage());
        }
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

    @Override
    public List<Payment> getAllPaymentsSimple() {
        try {
            return paymentRepository.findAll();
        } catch (Exception e) {
            logger.error("Error getting all payments: {}", e.getMessage());
            throw new RuntimeException("Failed to retrieve all payments: " + e.getMessage());
        }
    }

    @Override
    public List<Payment> getPaymentsByClient(String clientId) {
        try {
            if (clientId == null || clientId.isEmpty()) {
                throw new IllegalArgumentException("Client ID cannot be null or empty");
            }
            return paymentRepository.findByClientId(clientId);
        } catch (Exception e) {
            logger.error("Error getting payments for client {}: {}", clientId, e.getMessage());
            throw new RuntimeException("Failed to retrieve payments for client: " + e.getMessage());
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