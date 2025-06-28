package com.example.ExpedNow.controllers;

import com.example.ExpedNow.models.Payment;
import com.example.ExpedNow.models.enums.PaymentMethod;
import com.example.ExpedNow.models.enums.PaymentStatus;
import com.example.ExpedNow.services.core.DeliveryPaymentServiceInterface;
import com.example.ExpedNow.services.core.PaymentServiceInterface;
import com.example.ExpedNow.services.core.DeliveryServiceInterface; // Add this import
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final DeliveryPaymentServiceInterface deliveryPaymentService; // أضف هذا السطر

    @Autowired
    private PaymentServiceInterface paymentService;
    @Autowired
    private DeliveryServiceInterface deliveryService; // Fix the type here

    public PaymentController(DeliveryPaymentServiceInterface deliveryPaymentService) {
        this.deliveryPaymentService = deliveryPaymentService;
    }

    // CREATE PAYMENT
    @PostMapping
    public ResponseEntity<Map<String, Object>> createPayment(@Valid @RequestBody Payment payment) {
        try {
            Payment createdPayment = paymentService.createPayment(payment);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment created successfully");

            // Add these required fields at the root level
            response.put("paymentId", createdPayment.getId());

            if (createdPayment.getPaymentMethod() == PaymentMethod.CREDIT_CARD) {
                response.put("clientSecret", createdPayment.getClientSecret());
            }

            // Keep the full payment in data for reference
            response.put("data", createdPayment);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }catch (IllegalArgumentException e) {
            logger.error("Invalid payment data: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            logger.error("Error creating payment: {}", e.getMessage(), e);
            // Log the full stack trace
            logger.error("Full stack trace: ", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to create payment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // GET PAYMENT BY ID
    @GetMapping("/{paymentId}")
    public ResponseEntity<Map<String, Object>> getPaymentById(@PathVariable String paymentId) {
        try {
            Payment payment = paymentService.getPaymentById(paymentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", payment);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Payment not found: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        } catch (Exception e) {
            logger.error("Error retrieving payment: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve payment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/{paymentId}/status")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(@PathVariable String paymentId) {
        try {
            Payment payment = paymentService.getPaymentById(paymentId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("status", payment.getStatus());
            response.put("updatedAt", payment.getUpdatedAt());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Payment not found: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        } catch (Exception e) {
            logger.error("Error retrieving payment status: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve payment status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmPayment(
            @RequestBody Map<String, Object> requestBody) {

        logger.info("Received confirmPayment request with body: {}", requestBody);

        // 1. Request validation
        if (requestBody == null) {
            return buildErrorResponse("Request body cannot be null", HttpStatus.BAD_REQUEST);
        }

        // 2. Parameter extraction with validation
        String transactionId;
        double amount;

        try {
            transactionId = Optional.ofNullable(requestBody.get("transactionId"))
                    .map(Object::toString)
                    .filter(s -> !s.trim().isEmpty())
                    .orElseThrow(() -> new IllegalArgumentException("transactionId is required and cannot be empty"));

            Object amountObj = requestBody.get("amount");
            if (amountObj == null) {
                throw new IllegalArgumentException("amount is required");
            }

            try {
                amount = Double.parseDouble(amountObj.toString());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("amount must be a valid number");
            }

            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid payment confirmation request: {}", e.getMessage());
            return buildErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        try {
            logger.info("Confirming payment - transactionId: {}, amount: {}", transactionId, amount);

            Payment confirmedPayment = paymentService.confirmPayment(transactionId, amount);

            // 4. Build success response with data wrapper
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Payment confirmed successfully");

            // Wrap the payment details in a "data" object
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", confirmedPayment.getId());
            data.put("transactionId", confirmedPayment.getTransactionId());
            data.put("amount", confirmedPayment.getAmount());
            data.put("status", confirmedPayment.getStatus());
            if (confirmedPayment.getDeliveryId() != null) {
                data.put("deliveryId", confirmedPayment.getDeliveryId());
            }

            response.put("data", data);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Payment confirmation failed - validation error: {}", e.getMessage());
            return buildErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Payment confirmation failed - server error: {}", e.getMessage(), e);
            return buildErrorResponse("Failed to confirm payment: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Helper method for error responses
    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(status).body(response);
    }

    // GET ALL PAYMENTS WITH PAGINATION AND FILTERS
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String deliveryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {
        try {
            Sort sort = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<Payment> payments = paymentService.getAllPayments(status, method, clientId, deliveryId, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", payments.getContent());
            response.put("pagination", Map.of(
                    "currentPage", payments.getNumber(),
                    "totalPages", payments.getTotalPages(),
                    "totalElements", payments.getTotalElements(),
                    "hasNext", payments.hasNext(),
                    "hasPrevious", payments.hasPrevious()
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving payments: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve payments: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // GET ALL PAYMENTS SIMPLE (WITHOUT PAGINATION)
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllPaymentsSimple() {
        try {
            List<Payment> payments = paymentService.getAllPaymentsSimple();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", payments);
            response.put("count", payments.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving all payments: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve payments: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // GET PAYMENTS BY CLIENT - with additional security
    // GET PAYMENTS BY CLIENT - with debugging
    @GetMapping("/client/{clientId}")
    public ResponseEntity<Map<String, Object>> getPaymentsByClient(@PathVariable String clientId) {
        logger.info("=== DEBUG: getPaymentsByClient called ===");
        logger.info("Client ID: {}", clientId);

        try {
            // Check if clientId is valid
            if (clientId == null || clientId.trim().isEmpty()) {
                logger.error("Client ID is null or empty");
                throw new IllegalArgumentException("Client ID cannot be null or empty");
            }

            logger.info("Calling paymentService.getPaymentsByClient with clientId: {}", clientId);

            // Check if paymentService is injected properly
            if (paymentService == null) {
                logger.error("PaymentService is null!");
                throw new RuntimeException("PaymentService not properly injected");
            }

            List<Payment> payments = paymentService.getPaymentsByClient(clientId);
            logger.info("Successfully retrieved {} payments for client {}",
                    payments != null ? payments.size() : 0, clientId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", payments);
            response.put("count", payments != null ? payments.size() : 0);

            logger.info("Returning successful response");
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid client ID: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);

        } catch (Exception e) {
            logger.error("Error retrieving payments for client: {}", e.getMessage(), e);
            logger.error("Exception type: {}", e.getClass().getSimpleName());
            logger.error("Full stack trace: ", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve client payments: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    // GET PAYMENTS BY DELIVERY
    @GetMapping("/delivery/{deliveryId}")
    public ResponseEntity<Map<String, Object>> getPaymentsByDelivery(@PathVariable String deliveryId) {
        try {
            List<Payment> payments = paymentService.getPaymentsByDelivery(deliveryId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", payments);
            response.put("count", payments.size());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid delivery ID: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            logger.error("Error retrieving payments for delivery: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve delivery payments: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // PROCESS PAYMENT
    @PostMapping("/{paymentId}/process")
    public ResponseEntity<Map<String, Object>> processPayment(
            @PathVariable String paymentId,
            @RequestParam(required = false) String discountCode) {
        try {
            Payment processedPayment = paymentService.processPayment(paymentId, discountCode);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment processed successfully");
            response.put("data", processedPayment);

            logger.info("Payment processed successfully: {}", paymentId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid payment processing request: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            logger.error("Error processing payment: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to process payment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // GET CLIENT SECRET FOR STRIPE
    @GetMapping("/{paymentId}/client-secret")
    public ResponseEntity<Map<String, Object>> getPaymentClientSecret(@PathVariable String paymentId) {
        try {
            String clientSecret = paymentService.getPaymentClientSecret(paymentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("clientSecret", clientSecret);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid client secret request: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            logger.error("Error retrieving client secret: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve client secret: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // FAIL PAYMENT
    @PostMapping("/fail")
    public ResponseEntity<Map<String, Object>> failPayment(@RequestParam String transactionId) {
        try {
            Payment failedPayment = paymentService.failPayment(transactionId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment marked as failed");
            response.put("data", failedPayment);

            logger.info("Payment marked as failed: {}", transactionId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid payment failure request: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            logger.error("Error failing payment: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to update payment status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // UPDATE PAYMENT STATUS
    @PutMapping("/{paymentId}/status")
    public ResponseEntity<Map<String, Object>> updatePaymentStatus(
            @PathVariable String paymentId,
            @RequestParam String status) {
        try {
            Payment updatedPayment = paymentService.updatePaymentStatus(paymentId, status);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment status updated successfully");
            response.put("data", updatedPayment);

            logger.info("Payment status updated: {} to {}", paymentId, status);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid status update request: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            logger.error("Error updating payment status: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to update payment status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // CANCEL PAYMENT
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelPayment(@PathVariable String paymentId) {
        try {
            Payment cancelledPayment = paymentService.cancelPayment(paymentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment cancelled successfully");
            response.put("data", cancelledPayment);

            logger.info("Payment cancelled successfully: {}", paymentId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid payment cancellation: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            logger.error("Error cancelling payment: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to cancel payment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // REFUND PAYMENT
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<Map<String, Object>> refundPayment(
            @PathVariable String paymentId,
            @RequestParam(required = false) Double amount) {
        try {
            Payment refundedPayment = paymentService.refundPayment(paymentId, amount);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment refunded successfully");
            response.put("data", refundedPayment);

            logger.info("Payment refunded successfully: {} (amount: {})", paymentId, amount);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid refund request: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            logger.error("Error refunding payment: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to refund payment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // CHECK PAYMENT METHOD SUPPORT
    @GetMapping("/methods/{method}/supported")
    public ResponseEntity<Map<String, Object>> isPaymentMethodSupported(@PathVariable PaymentMethod method) {
        try {
            boolean isSupported = paymentService.isPaymentMethodSupported(method);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("method", method);
            response.put("supported", isSupported);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error checking payment method support: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to check payment method support: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // GET PAYMENT STATISTICS
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getPaymentStats() {
        try {
            Map<String, Object> stats = paymentService.getPaymentStats();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", stats);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving payment statistics: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve payment statistics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // PROCESS SPECIFIC PAYMENT METHODS
    @PostMapping("/{paymentId}/process/card")
    public ResponseEntity<Map<String, Object>> processCardPayment(
            @PathVariable String paymentId,
            @RequestParam(required = false) String discountCode) {
        try {
            Payment processedPayment = paymentService.processCardPayment(paymentId, discountCode);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Card payment processed successfully");
            response.put("data", processedPayment);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing card payment: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to process card payment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/{paymentId}/process/cash")
    public ResponseEntity<Map<String, Object>> processCashPayment(
            @PathVariable String paymentId,
            @RequestParam(required = false) String discountCode) {
        try {
            Payment processedPayment = paymentService.processCashPayment(paymentId, discountCode);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cash payment processed successfully");
            response.put("data", processedPayment);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing cash payment: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to process cash payment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/{paymentId}/process/wallet")
    public ResponseEntity<Map<String, Object>> processWalletPayment(
            @PathVariable String paymentId,
            @RequestParam(required = false) String discountCode) {
        try {
            Payment processedPayment = paymentService.processWalletPayment(paymentId, discountCode);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Wallet payment processed successfully");
            response.put("data", processedPayment);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing wallet payment: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to process wallet payment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/{paymentId}/process/bank-transfer")
    public ResponseEntity<Map<String, Object>> processBankTransferPayment(
            @PathVariable String paymentId,
            @RequestParam(required = false) String discountCode) {
        try {
            Payment processedPayment = paymentService.processBankTransferPayment(paymentId, discountCode);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Bank transfer payment processed successfully");
            response.put("data", processedPayment);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing bank transfer payment: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to process bank transfer payment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }


    @PostMapping("/{paymentId}/release-to-delivery")
    public ResponseEntity<?> releaseToDelivery(@PathVariable String paymentId) {
        try {
            deliveryPaymentService.processDeliveryPayment(paymentId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "تم تحويل الحصة للموصل بنجاح"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}