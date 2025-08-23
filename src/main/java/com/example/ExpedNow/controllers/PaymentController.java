package com.example.ExpedNow.controllers;

import com.example.ExpedNow.models.Payment;
import com.example.ExpedNow.services.core.UserServiceInterface;

import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.enums.PaymentMethod;
import com.example.ExpedNow.models.enums.PaymentStatus;
import com.example.ExpedNow.repositories.PaymentRepository;
import com.example.ExpedNow.services.core.DeliveryPaymentServiceInterface;
import com.example.ExpedNow.services.core.PaymentServiceInterface;
import com.example.ExpedNow.services.core.DeliveryServiceInterface;
import jakarta.validation.Valid;
import com.example.ExpedNow.repositories.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    @Autowired
    private MongoTemplate mongoTemplate;
    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final DeliveryPaymentServiceInterface deliveryPaymentService;
    @Autowired
    private UserServiceInterface userService;
    @Autowired
    private PaymentServiceInterface paymentService;
    @Autowired
    private DeliveryServiceInterface deliveryService;
    @Autowired
    private UserRepository userRepository;


    public PaymentController(DeliveryPaymentServiceInterface deliveryPaymentService) {
        this.deliveryPaymentService = deliveryPaymentService;
    }
    @Autowired
    private PaymentRepository paymentRepository;
    // CREATE PAYMENT
    @PostMapping
    public ResponseEntity<Map<String, Object>> createPayment(@Valid @RequestBody Payment payment) {
        try {
            // Add this logic to link delivery person

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
            @RequestBody Map<String, String> request) {  // Change to @RequestBody

        try {
            String status = request.get("status");  // Extract status from request body
            if (status == null || status.isEmpty()) {
                throw new IllegalArgumentException("Status parameter is required");
            }

            Payment updatedPayment = paymentService.updatePaymentStatus(paymentId, status);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment status updated successfully");
            response.put("data", updatedPayment);

            logger.info("Payment status updated: {} to {}", paymentId, status);
            return ResponseEntity.ok(response);
        }  catch (IllegalArgumentException e) {
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


    @GetMapping("/delivery-person/{deliveryPersonId}")
    public ResponseEntity<Map<String, Object>> getPaymentsForDeliveryPerson(
            @PathVariable String deliveryPersonId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean includeAll,
            @RequestParam(defaultValue = "false") boolean includeDeliveryPerson,
            Authentication authentication) {

        try {
            if (deliveryPersonId == null || deliveryPersonId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Delivery person ID is required"
                ));
            }

            // Get the authenticated user's email/username from JWT
            String authenticatedUserEmail = authentication.getName();
            logger.info("Authenticated user email: {}, Requested delivery person ID: {}",
                    authenticatedUserEmail, deliveryPersonId);

            // Resolve the authenticated user's actual database ID
            User authenticatedUser = null;
            try {
                authenticatedUser = userService.findByEmail(authenticatedUserEmail);
                if (authenticatedUser == null) {
                    // Try finding by username if email lookup fails
                    authenticatedUser = userService.findByUsername(authenticatedUserEmail);
                }
            } catch (Exception e) {
                logger.error("Failed to resolve authenticated user: {}", e.getMessage());
            }

            if (authenticatedUser == null) {
                logger.error("Could not resolve authenticated user: {}", authenticatedUserEmail);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "success", false,
                        "message", "Could not validate user identity"
                ));
            }

            String authenticatedUserId = authenticatedUser.getId();
            logger.info("Resolved authenticated user ID: {} for email: {}",
                    authenticatedUserId, authenticatedUserEmail);

            // Check authorization
            boolean hasDeliveryPersonRole = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("DELIVERY_PERSON") ||
                            auth.getAuthority().equals("PROFESSIONAL") ||
                            auth.getAuthority().equals("TEMPORARY"));

            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ADMIN"));

            boolean isOwner = authenticatedUserId.equals(deliveryPersonId);

            logger.info("Authorization check - hasDeliveryPersonRole: {}, isAdmin: {}, isOwner: {}",
                    hasDeliveryPersonRole, isAdmin, isOwner);

            if (!isAdmin && !isOwner) {
                logger.warn("Access denied: User {} (ID: {}) tried to access payments for delivery person {}",
                        authenticatedUserEmail, authenticatedUserId, deliveryPersonId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "message", "Access denied. You can only view your own payments."
                ));
            }

            // Get payments using the service method
            List<Payment> allPayments = paymentService.getPaymentsByDeliveryPerson(deliveryPersonId);

            // Calculate pagination
            int totalElements = allPayments.size();
            int totalPages = (int) Math.ceil((double) totalElements / size);
            int startIndex = page * size;
            int endIndex = Math.min(startIndex + size, totalElements);

            List<Payment> pagedPayments = totalElements > 0 ?
                    allPayments.subList(startIndex, endIndex) : new ArrayList<>();

            // Enrich payments with delivery person data if requested
            // Payments are already enriched by the service method
// No additional enrichment needed since getPaymentsByDeliveryPerson already calls enrichPayment
            // Calculate summary data
            double totalAmount = allPayments.stream()
                    .mapToDouble(p -> p.getDeliveryPersonShare() != null ? p.getDeliveryPersonShare() : 0.0)
                    .sum();

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", pagedPayments);
            response.put("pagination", Map.of(
                    "currentPage", page,
                    "totalPages", totalPages,
                    "totalElements", totalElements,
                    "hasNext", page < totalPages - 1,
                    "hasPrevious", page > 0,
                    "pageSize", size
            ));
            response.put("summary", Map.of(
                    "totalEarnings", totalAmount,
                    "totalPayments", totalElements,
                    "averagePayment", totalElements > 0 ? totalAmount / totalElements : 0.0
            ));

            logger.info("Successfully retrieved {} payments for delivery person {}",
                    totalElements, deliveryPersonId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting payments for delivery person {}: {}", deliveryPersonId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Failed to get payments: " + e.getMessage()
            ));
        }
    }
    @GetMapping("/delivery-person/{deliveryPersonId}/summary")
    public ResponseEntity<Map<String, Object>> getDeliveryPersonPaymentSummary(
            @PathVariable String deliveryPersonId,
            Authentication authentication) {

        try {
            // Get the authenticated user's email/username from JWT
            String authenticatedUserEmail = authentication.getName();

            // Resolve the authenticated user's actual database ID
            User authenticatedUser = userService.findByEmail(authenticatedUserEmail);
            if (authenticatedUser == null) {
                authenticatedUser = userService.findByUsername(authenticatedUserEmail);
            }

            if (authenticatedUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "success", false,
                        "message", "Could not validate user identity"
                ));
            }

            String authenticatedUserId = authenticatedUser.getId();

            // Check authorization
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ADMIN"));
            boolean isOwner = authenticatedUserId.equals(deliveryPersonId);

            if (!isAdmin && !isOwner) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "message", "Access denied. You can only view your own payment summary."
                ));
            }

            List<Payment> payments = paymentService.getPaymentsByDeliveryPerson(deliveryPersonId);

            double totalEarnings = payments.stream()
                    .mapToDouble(p -> p.getDeliveryPersonShare() != null ? p.getDeliveryPersonShare() : 0.0)
                    .sum();

            // Calculate monthly earnings
            LocalDateTime now = LocalDateTime.now();
            double monthlyEarnings = payments.stream()
                    .filter(p -> p.getDeliveryPersonPaidAt() != null &&
                            p.getDeliveryPersonPaidAt().getMonth() == now.getMonth() &&
                            p.getDeliveryPersonPaidAt().getYear() == now.getYear())
                    .mapToDouble(p -> p.getDeliveryPersonShare() != null ? p.getDeliveryPersonShare() : 0.0)
                    .sum();

            // Get latest payment
            Payment latestPayment = payments.stream()
                    .max((p1, p2) -> {
                        LocalDateTime date1 = p1.getDeliveryPersonPaidAt();
                        LocalDateTime date2 = p2.getDeliveryPersonPaidAt();
                        if (date1 == null && date2 == null) return 0;
                        if (date1 == null) return -1;
                        if (date2 == null) return 1;
                        return date1.compareTo(date2);
                    })
                    .orElse(null);

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalEarnings", totalEarnings);
            summary.put("monthlyEarnings", monthlyEarnings);
            summary.put("totalPayments", payments.size());
            summary.put("averagePayment", payments.size() > 0 ? totalEarnings / payments.size() : 0.0);
            summary.put("lastPaymentAmount", latestPayment != null ? latestPayment.getDeliveryPersonShare() : 0.0);
            summary.put("lastPaymentDate", latestPayment != null ? latestPayment.getDeliveryPersonPaidAt() : null);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", summary
            ));

        } catch (Exception e) {
            logger.error("Error getting payment summary for delivery person: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Failed to get payment summary"
            ));
        }
    }
    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/{paymentId}/release-to-delivery")
    public ResponseEntity<Map<String, Object>> releaseToDeliveryPerson(@PathVariable String paymentId) {
        try {
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

            // Validate payment can be released
            if (payment.getStatus() != PaymentStatus.COMPLETED) {
                throw new IllegalArgumentException("Only completed payments can be released");
            }

            if (payment.getDeliveryPersonId() == null) {
                throw new IllegalArgumentException("No delivery person assigned to this payment");
            }

            if (payment.getDeliveryPersonPaid() != null && payment.getDeliveryPersonPaid()) {
                throw new IllegalArgumentException("Payment already released to delivery person");
            }

            // Calculate 80% share using the correct amount
            double finalAmount = payment.getFinalAmountAfterDiscount() != null ?
                    payment.getFinalAmountAfterDiscount() : payment.getAmount();

            if (finalAmount <= 0) {
                throw new IllegalArgumentException("Invalid payment amount for release");
            }

            double shareAmount = finalAmount * 0.8;

            // Update payment
            payment.setDeliveryPersonShare(shareAmount);
            payment.setDeliveryPersonPaid(true);
            payment.setDeliveryPersonPaidAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            Payment updatedPayment = paymentRepository.save(payment);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment released to delivery person successfully");
            response.put("paymentId", updatedPayment.getId());
            response.put("deliveryPersonId", updatedPayment.getDeliveryPersonId());
            response.put("amountReleased", shareAmount);
            response.put("releaseDate", updatedPayment.getDeliveryPersonPaidAt());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Validation error: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logger.error("Error releasing payment: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to release payment");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    // In BonusController.java
    @GetMapping("/delivery-persons")
    public ResponseEntity<List<Map<String, String>>> getDeliveryPersons() {
        try {
            List<User> deliveryPersons = userRepository.findByRole("DELIVERY_PERSON");
            List<Map<String, String>> result = deliveryPersons.stream()
                    .map(dp -> {
                        Map<String, String> person = new HashMap<>();
                        person.put("id", dp.getId());
                        person.put("fullName", dp.getFullName()); // Assuming you have a fullName field
                        return person;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error retrieving delivery persons: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}