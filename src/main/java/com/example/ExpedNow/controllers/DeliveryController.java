package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.*;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.models.Mission;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.enums.PackageType;
import com.example.ExpedNow.models.enums.PaymentMethod;
import com.example.ExpedNow.models.enums.PaymentStatus;
import com.example.ExpedNow.security.CustomUserDetailsService;
import com.example.ExpedNow.services.core.MissionServiceInterface;
import com.example.ExpedNow.services.core.UserServiceInterface;
import com.example.ExpedNow.services.core.impl.DeliveryServiceImpl;
import com.example.ExpedNow.services.core.impl.ImageAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryController.class);
    private final DeliveryServiceImpl deliveryService;
    private final MissionServiceInterface missionService;
    private final UserServiceInterface userService;
    private final ImageAnalysisService imageAnalysisService;

    public DeliveryController(DeliveryServiceImpl deliveryService,
                              MissionServiceInterface missionService,
                              UserServiceInterface userService,
                              ImageAnalysisService imageAnalysisService) {
        this.deliveryService = deliveryService;
        this.missionService = missionService;
        this.userService = userService;
        this.imageAnalysisService = imageAnalysisService;
    }

    @PostMapping("/request-with-image")
    @PreAuthorize("hasRole('CLIENT') or hasRole('INDIVIDUAL') or hasRole('ENTERPRISE')")
    public ResponseEntity<?> createDeliveryRequestWithImage(
            @RequestPart("delivery") @Valid DeliveryRequestDTO requestDTO,
            @RequestPart("image") MultipartFile imageFile,
            @RequestParam(required = false) String clientId) {

        try {
            // Validate image file
            if (imageFile == null || imageFile.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Image file is required"));
            }

            // Validate file type
            String contentType = imageFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "File must be an image"));
            }

            // Create DeliveryRequest from DTO
            DeliveryRequest delivery = createDeliveryFromDTO(requestDTO, clientId);

            // Create delivery with image analysis
            DeliveryRequest savedDelivery = deliveryService.createDeliveryWithImage(delivery, imageFile);

            Map<String, Object> response = new HashMap<>();
            response.put("delivery", convertToDto(savedDelivery));
            response.put("imageAnalyzed", savedDelivery.isImageAnalyzed());
            response.put("imageQuality", savedDelivery.getImageQuality());

            return new ResponseEntity<>(response, HttpStatus.CREATED);

        } catch (Exception e) {
            logger.error("Error creating delivery with image: {}", e.getMessage(), e);
            return new ResponseEntity<>(
                    Map.of("error", "Error creating delivery: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    // Re-analyze image for existing delivery
    @PostMapping("/{deliveryId}/reanalyze-image")
    @PreAuthorize("hasRole('CLIENT') or hasRole('INDIVIDUAL') or hasRole('ENTERPRISE')")
    public ResponseEntity<?> reanalyzeImage(
            @PathVariable String deliveryId,
            @RequestPart("image") MultipartFile imageFile,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            String clientId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();

            // Verify ownership
            DeliveryRequest delivery = deliveryService.getDeliveryById(deliveryId);
            if (!delivery.getClientId().equals(clientId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Not authorized to modify this delivery"));
            }

            // Validate image file
            if (imageFile == null || imageFile.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Image file is required"));
            }

            String contentType = imageFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "File must be an image"));
            }

            DeliveryRequest updatedDelivery = deliveryService.reanalyzeImage(deliveryId, imageFile);

            Map<String, Object> response = new HashMap<>();
            response.put("delivery", convertToDto(updatedDelivery));
            response.put("imageAnalyzed", updatedDelivery.isImageAnalyzed());
            response.put("imageQuality", updatedDelivery.getImageQuality());
            response.put("extractedText", updatedDelivery.getExtractedText());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error reanalyzing image: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error reanalyzing image: " + e.getMessage()));
        }
    }

    // Get image analysis results
    @GetMapping("/{deliveryId}/image-analysis")
    @PreAuthorize("hasRole('CLIENT') or hasRole('INDIVIDUAL') or hasRole('ENTERPRISE') or hasRole('ADMIN')")
    public ResponseEntity<?> getImageAnalysis(
            @PathVariable String deliveryId,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            String userId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();

            // Verify ownership (clients can only see their own, admins can see all)
            DeliveryRequest delivery = deliveryService.getDeliveryById(deliveryId);
            boolean isAdmin = userDetails.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdmin && !delivery.getClientId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Not authorized to view this delivery's analysis"));
            }

            if (!delivery.isImageAnalyzed()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No image analysis available for this delivery"));
            }

            ImageAnalysisResponse analysis = deliveryService.getImageAnalysis(deliveryId);
            return ResponseEntity.ok(analysis);

        } catch (Exception e) {
            logger.error("Error getting image analysis: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error retrieving image analysis: " + e.getMessage()));
        }
    }

    @GetMapping("/{deliveryId}/with-assigned")
    public ResponseEntity<Map<String, Object>> getDeliveryWithAssignedPerson(@PathVariable String deliveryId) {
        try {
            DeliveryRequest delivery = deliveryService.getDeliveryById(deliveryId);
            Map<String, Object> response = new HashMap<>();
            response.put("delivery", convertToDto(delivery));

            if (delivery.getDeliveryPersonId() != null) {
                User deliveryPerson = userService.findById(delivery.getDeliveryPersonId());

                Map<String, Object> personInfo = new HashMap<>();
                personInfo.put("id", deliveryPerson.getId());
                personInfo.put("firstName", deliveryPerson.getFirstName() != null ? deliveryPerson.getFirstName() : "");
                personInfo.put("lastName", deliveryPerson.getLastName() != null ? deliveryPerson.getLastName() : "");
                personInfo.put("fullName", deliveryPerson.getFullName());
                personInfo.put("phone", deliveryPerson.getPhone() != null ? deliveryPerson.getPhone() : "");
                personInfo.put("email", deliveryPerson.getEmail());
                personInfo.put("rating", deliveryPerson.getRating());
                personInfo.put("ratingCount", deliveryPerson.getRatingCount());
                personInfo.put("completedDeliveries", deliveryPerson.getCompletedDeliveries());

                if (deliveryPerson.getAssignedVehicle() != null) {
                    Map<String, Object> vehicleInfo = new HashMap<>();
                    vehicleInfo.put("model", deliveryPerson.getAssignedVehicle().getModel());
                    vehicleInfo.put("licensePlate", deliveryPerson.getAssignedVehicle().getLicensePlate());
                    vehicleInfo.put("type", deliveryPerson.getAssignedVehicle().getVehicleType());
                    personInfo.put("vehicle", vehicleInfo);
                }

                response.put("assignedDeliveryPerson", personInfo);
            }

            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{deliveryId}/payment-status")
    public ResponseEntity<Map<String, Object>> updateDeliveryPaymentStatus(
            @PathVariable String deliveryId,
            @RequestBody Map<String, String> paymentUpdate) {

        try {
            if (!paymentUpdate.containsKey("paymentId") || !paymentUpdate.containsKey("paymentStatus")) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Missing required fields: paymentId or paymentStatus");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            PaymentStatus paymentStatus;
            try {
                paymentStatus = PaymentStatus.valueOf(paymentUpdate.get("paymentStatus"));
            } catch (IllegalArgumentException e) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Invalid payment status: " + paymentUpdate.get("paymentStatus"));
                return ResponseEntity.badRequest().body(errorResponse);
            }

            DeliveryRequest delivery = deliveryService.updateDeliveryPaymentStatus(
                    deliveryId,
                    paymentUpdate.get("paymentId"),
                    paymentStatus
            );

            if (paymentUpdate.containsKey("paymentMethod")) {
                try {
                    PaymentMethod paymentMethod = PaymentMethod.valueOf(paymentUpdate.get("paymentMethod"));
                    delivery.setPreferredPaymentMethod(paymentMethod);
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid payment method provided: " + paymentUpdate.get("paymentMethod"));
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", delivery);
            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        } catch (Exception e) {
            logger.error("Error updating delivery payment status", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Internal server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/request")
    @PreAuthorize("hasRole('CLIENT') or hasRole('INDIVIDUAL') or hasRole('ENTERPRISE')")
    public ResponseEntity<?> createDeliveryRequest(
            @Valid @RequestBody DeliveryRequestDTO requestDTO,
            @RequestParam(required = false) String clientId) {

        try {
            DeliveryRequest delivery = createDeliveryFromDTO(requestDTO, clientId);
            DeliveryRequest savedDelivery = deliveryService.createDelivery(delivery);
            return new ResponseEntity<>(convertToDto(savedDelivery), HttpStatus.CREATED);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error creating delivery: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> adminCancelDelivery(@PathVariable String id) {
        deliveryService.cancelDelivery(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/client/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Void> clientCancelDelivery(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String clientId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();
        deliveryService.cancelDelivery(id, clientId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/expire-old")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> expireOldDeliveries() {
        deliveryService.expireOldDeliveries();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('DELIVERY_PERSON')")
    @Transactional
    public ResponseEntity<?> acceptDelivery(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {

        String deliveryPersonId = request.get("deliveryPersonId");

        try {
            logger.info("Accepting delivery {} by user {}", id, deliveryPersonId);

            DeliveryRequest delivery = deliveryService.getDeliveryById(id);

            if (!deliveryPersonId.equals(delivery.getDeliveryPersonId())) {
                logger.warn("Unauthorized access attempt by user {}", deliveryPersonId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Not authorized to accept this delivery");
            }

            if (delivery.getStatus() != DeliveryRequest.DeliveryReqStatus.ASSIGNED &&
                    delivery.getStatus() != DeliveryRequest.DeliveryReqStatus.PENDING) {
                return ResponseEntity.badRequest()
                        .body("Cannot accept delivery with status: " + delivery.getStatus());
            }

            Mission newMission = missionService.createMission(id, deliveryPersonId);
            logger.info("New mission created with ID: {}", newMission.getId());

            delivery = deliveryService.updateDeliveryStatus(id, DeliveryRequest.DeliveryReqStatus.APPROVED);

            Map<String, Object> response = new HashMap<>();
            response.put("delivery", convertToDto(delivery));
            response.put("mission", newMission);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error in acceptDelivery:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error accepting delivery: " + e.getMessage());
        }
    }

    @GetMapping("/assigned-pending")
    @PreAuthorize("hasRole('DELIVERY_PERSON')")
    public ResponseEntity<List<DeliveryResponseDTO>> getAssignedPendingDeliveries(
            @AuthenticationPrincipal UserDetails userDetails) {

        String deliveryPersonId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();
        logger.info("User ID: {}", deliveryPersonId);

        List<DeliveryResponseDTO> deliveries = deliveryService.getAssignedPendingDeliveries(deliveryPersonId);
        logger.info("Raw deliveries from DB: {}", deliveries);

        return ResponseEntity.ok(deliveries);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('DELIVERY_PERSON')")
    public ResponseEntity<DeliveryResponseDTO> rejectDelivery(
            @PathVariable String id,
            @RequestParam String deliveryPersonId) {

        DeliveryRequest updated = deliveryService.resetDeliveryAssignment(id, deliveryPersonId);
        return ResponseEntity.ok(convertToDto(updated));
    }

    @GetMapping
    @PreAuthorize("hasRole('CLIENT') or hasRole('INDIVIDUAL') or hasRole('ENTERPRISE') or hasRole('ADMIN')")
    public ResponseEntity<List<DeliveryResponseDTO>> getDeliveries(
            @RequestParam(required = false) String clientId) {

        List<DeliveryRequest> deliveries;

        if (clientId != null && !clientId.isEmpty()) {
            deliveries = deliveryService.getClientDeliveries(clientId);
        } else {
            deliveries = deliveryService.getAllDeliveries();
        }

        List<DeliveryResponseDTO> responseDTOs = deliveries.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDTOs);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('DELIVERY_PERSON') or hasRole('ADMIN')")
    public ResponseEntity<List<DeliveryResponseDTO>> getPendingDeliveries() {

        List<DeliveryRequest> pendingDeliveries = deliveryService.getPendingDeliveries();

        List<DeliveryResponseDTO> responseDTOs = pendingDeliveries.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDTOs);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable String id,
            @RequestBody StatusUpdateRequest statusRequest) {

        try {
            DeliveryRequest.DeliveryReqStatus newStatus =
                    DeliveryRequest.DeliveryReqStatus.valueOf(statusRequest.status().toUpperCase());

            DeliveryRequest updatedDelivery = deliveryService.updateDeliveryStatus(id, newStatus);
            return ResponseEntity.ok(convertToDto(updatedDelivery));

        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid status value");
            errorResponse.put("validStatuses", Arrays.toString(DeliveryRequest.DeliveryReqStatus.values()));
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('DELIVERY_PERSON')")
    public ResponseEntity<List<DeliveryResponseDTO>> getDeliveryHistory(
            @AuthenticationPrincipal UserDetails userDetails) {

        String deliveryPersonId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();
        logger.info("Fetching delivery history for user: {}", deliveryPersonId);

        List<DeliveryResponseDTO> history = deliveryService.getDeliveryHistory(deliveryPersonId);
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CLIENT') or hasRole('INDIVIDUAL') or hasRole('ENTERPRISE') or hasRole('ADMIN')")
    public ResponseEntity<Void> cancelDelivery(@PathVariable String id) {
        deliveryService.cancelDelivery(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryRequest> getDeliveryById(@PathVariable String deliveryId) {
        DeliveryRequest delivery = deliveryService.getDeliveryById(deliveryId);
        return ResponseEntity.ok(delivery);
    }

    @PostMapping("/{deliveryId}/rate")
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENT','ROLE_INDIVIDUAL','ROLE_ENTERPRISE')")
    public ResponseEntity<?> rateDelivery(
            @PathVariable String deliveryId,
            @Valid @RequestBody RatingRequest ratingRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String clientId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();

            DeliveryRequest delivery = deliveryService.getDeliveryById(deliveryId);

            if (delivery.getStatus() != DeliveryRequest.DeliveryReqStatus.DELIVERED) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Delivery not completed yet"));
            }

            if (!delivery.getClientId().equals(clientId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Not authorized to rate this delivery"));
            }

            if (delivery.isRated()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Delivery already rated"));
            }

            deliveryService.rateDelivery(deliveryId, ratingRequest.rating(), clientId);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/analyze-image")
    @PreAuthorize("hasRole('CLIENT') or hasRole('INDIVIDUAL') or hasRole('ENTERPRISE')")
    public ResponseEntity<?> analyzeImageStandalone(
            @RequestPart("image") MultipartFile imageFile) {

        try {
            // Validate image file
            if (imageFile == null || imageFile.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Image file is required"));
            }

            // Validate file type
            String contentType = imageFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "File must be an image"));
            }

            // Analyze the image using your service
            ImageAnalysisResponse analysis = imageAnalysisService.analyzeImage(imageFile);
            return ResponseEntity.ok(analysis);

        } catch (Exception e) {
            logger.error("Error in standalone image analysis: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error analyzing image: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/with-details")
    public DeliveryResponseDTO getDeliveryWithDetails(@PathVariable String id) {
        return deliveryService.getDeliveryWithDetails(id);
    }

    @PostMapping("/{deliveryId}/detailed-rating")
    @PreAuthorize("hasAnyRole('CLIENT','INDIVIDUAL','ENTERPRISE')")
    public ResponseEntity<?> submitDetailedRating(
            @PathVariable String deliveryId,
            @Valid @RequestBody DetailedRatingRequest ratingRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String clientId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();

            DeliveryRequest delivery = deliveryService.getDeliveryById(deliveryId);

            // Vérifications de sécurité
            if (!delivery.getClientId().equals(clientId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Not authorized to rate this delivery"));
            }

            if (delivery.getStatus() != DeliveryRequest.DeliveryReqStatus.DELIVERED) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Delivery not completed yet"));
            }

            if (delivery.isRated()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Delivery already rated"));
            }

            // Création de l'évaluation détaillée
            DetailedRatingResponse rating = deliveryService.submitDetailedRating(
                    deliveryId, ratingRequest, clientId
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Rating submitted successfully",
                    "rating", rating
            ));

        } catch (Exception e) {
            logger.error("Error submitting detailed rating: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/delivery-person/{deliveryPersonId}/ratings")
    @PreAuthorize("hasRole('DELIVERY_PERSON') or hasRole('ADMIN')")
    public ResponseEntity<List<DetailedRatingResponse>> getDeliveryPersonRatings(
            @PathVariable String deliveryPersonId) {

        // Implementation to get ratings for a specific delivery person
        List<DetailedRatingResponse> ratings = deliveryService.getDeliveryPersonRatings(deliveryPersonId);
        return ResponseEntity.ok(ratings);
    }

    @GetMapping("/rating-statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getRatingStatistics(
            @RequestParam(required = false) String deliveryPersonId,
            @RequestParam(required = false, defaultValue = "30") int days) {
        try {
            RatingStatistics stats = deliveryService.getRatingStatistics(deliveryPersonId, days);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error getting rating statistics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error retrieving statistics"));
        }
    }

    @GetMapping("/ratings/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DetailedRatingResponse>> getAllRatings() {
        try {
            List<DetailedRatingResponse> ratings = deliveryService.getAllRatings();
            return ResponseEntity.ok(ratings);
        } catch (Exception e) {
            logger.error("Error getting all ratings: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/performance/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DeliveryPersonPerformanceDTO>> getDeliveryPersonPerformanceStats() {
        try {
            List<DeliveryPersonPerformanceDTO> performanceStats = deliveryService.getDeliveryPersonPerformanceStats();
            return ResponseEntity.ok(performanceStats);
        } catch (Exception e) {
            logger.error("Error getting performance stats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/{deliveryId}/receipt")
    @PreAuthorize("hasAnyRole('CLIENT','INDIVIDUAL','ENTERPRISE','ADMIN')")
    public ResponseEntity<byte[]> downloadReceipt(
            @PathVariable String deliveryId,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            String userId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();
            DeliveryRequest delivery = deliveryService.getDeliveryById(deliveryId);

            // Check authorization
            boolean isAdmin = userDetails.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdmin && !delivery.getClientId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Generate receipt (you'll need to implement this service)
            byte[] receiptPdf = generateReceiptPdf(delivery);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "receipt-" + deliveryId + ".pdf");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(receiptPdf);

        } catch (Exception e) {
            logger.error("Error generating receipt: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/client/{clientId}/rating-history")
    @PreAuthorize("hasAnyRole('CLIENT','INDIVIDUAL','ENTERPRISE','ADMIN')")
    public ResponseEntity<?> getClientRatingHistory(
            @PathVariable String clientId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String userId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();
            boolean isAdmin = userDetails.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdmin && !clientId.equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Not authorized to view this rating history"));
            }

            List<DetailedRatingResponse> ratings = deliveryService.getClientRatingHistory(clientId);
            return ResponseEntity.ok(ratings);

        } catch (Exception e) {
            logger.error("Error getting client rating history: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error retrieving rating history"));
        }
    }

    @GetMapping("/{deliveryId}/detailed-rating")
    @PreAuthorize("hasAnyRole('CLIENT','INDIVIDUAL','ENTERPRISE','ADMIN','DELIVERY_PERSON')")
    public ResponseEntity<?> getDetailedRating(
            @PathVariable String deliveryId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String userId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();
            DeliveryRequest delivery = deliveryService.getDeliveryById(deliveryId);

            // Vérification des autorisations
            boolean isAdmin = userDetails.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
            boolean isClient = delivery.getClientId().equals(userId);
            boolean isDeliveryPerson = delivery.getDeliveryPersonId() != null &&
                    delivery.getDeliveryPersonId().equals(userId);

            if (!isAdmin && !isClient && !isDeliveryPerson) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Not authorized to view this rating"));
            }

            if (!delivery.isRated()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No rating found for this delivery"));
            }

            DetailedRatingResponse rating = deliveryService.getDetailedRating(deliveryId);
            return ResponseEntity.ok(rating);

        } catch (Exception e) {
            logger.error("Error getting detailed rating: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error retrieving rating"));
        }
    }

    @GetMapping("/today-completed")
    @PreAuthorize("hasRole('DELIVERY_PERSON')")
    public ResponseEntity<Integer> getTodayCompletedDeliveries(
            @AuthenticationPrincipal UserDetails userDetails) {

        String deliveryPersonId = ((CustomUserDetailsService.CustomUserDetails) userDetails).getUserId();
        int count = deliveryService.countTodayCompletedDeliveries(deliveryPersonId);
        return ResponseEntity.ok(count);
    }

    // Helper method to create DeliveryRequest from DTO
    private DeliveryRequest createDeliveryFromDTO(DeliveryRequestDTO requestDTO, String clientId) {
        DeliveryRequest delivery = new DeliveryRequest();
        delivery.setPickupAddress(requestDTO.pickupAddress());
        delivery.setDeliveryAddress(requestDTO.deliveryAddress());
        delivery.setPackageDescription(requestDTO.packageDescription());
        delivery.setPackageWeight(requestDTO.packageWeight());
        delivery.setClientId(requestDTO.clientId() != null ? requestDTO.clientId() : clientId);

        // Handle package type safely
        if (requestDTO.packageType() != null && !requestDTO.packageType().isEmpty()) {
            try {
                PackageType packageType = PackageType.valueOf(requestDTO.packageType());
                delivery.setPackageType(packageType);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid package type: {}", requestDTO.packageType());
            }
        }

        // Optional vehicle ID
        if (requestDTO.vehicleId() != null && !requestDTO.vehicleId().isEmpty()) {
            delivery.setVehicleId(requestDTO.vehicleId());
        }

        // Handle scheduled date
        Date scheduledDateFromDTO = requestDTO.scheduledDate();
        if (scheduledDateFromDTO != null) {
            delivery.setScheduledDate(scheduledDateFromDTO.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime());
        }

        delivery.setAdditionalInstructions(requestDTO.additionalInstructions());

        // Set coordinates if present
        if (requestDTO.pickupLatitude() != null) {
            delivery.setPickupLatitude(requestDTO.pickupLatitude());
        }
        if (requestDTO.pickupLongitude() != null) {
            delivery.setPickupLongitude(requestDTO.pickupLongitude());
        }
        if (requestDTO.deliveryLatitude() != null) {
            delivery.setDeliveryLatitude(requestDTO.deliveryLatitude());
        }
        if (requestDTO.deliveryLongitude() != null) {
            delivery.setDeliveryLongitude(requestDTO.deliveryLongitude());
        }

        delivery.setStatus(DeliveryRequest.DeliveryReqStatus.PENDING);
        delivery.setCreatedAt(LocalDateTime.now());

        return delivery;
    }

    // Helper method to map Entity to DTO
    private DeliveryResponseDTO convertToDto(DeliveryRequest delivery) {
        Date scheduledDate = delivery.getScheduledDate() != null ?
                Date.from(delivery.getScheduledDate().atZone(ZoneId.systemDefault()).toInstant()) : null;
        Date createdAt = delivery.getCreatedAt() != null ?
                Date.from(delivery.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()) : null;
        Date updatedAt = delivery.getUpdatedAt() != null ?
                Date.from(delivery.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()) : null;
        Date assignedAt = delivery.getAssignedAt() != null ?
                Date.from(delivery.getAssignedAt().atZone(ZoneId.systemDefault()).toInstant()) : null;
        Date startedAt = delivery.getStartedAt() != null ?
                Date.from(delivery.getStartedAt().atZone(ZoneId.systemDefault()).toInstant()) : null;
        Date completedAt = delivery.getCompletedAt() != null ?
                Date.from(delivery.getCompletedAt().atZone(ZoneId.systemDefault()).toInstant()) : null;

        return new DeliveryResponseDTO(
                delivery.getId(),
                delivery.getPickupAddress(),
                delivery.getDeliveryAddress(),
                delivery.getPackageDescription(),
                delivery.getPackageWeight(),
                delivery.getVehicleId(),
                scheduledDate,
                delivery.getAdditionalInstructions(),
                delivery.getStatus().name(),
                createdAt,
                delivery.getClientId(),
                updatedAt,
                assignedAt,
                startedAt,
                completedAt,
                delivery.getPickupLatitude(),
                delivery.getPickupLongitude(),
                delivery.getDeliveryLatitude(),
                delivery.getDeliveryLongitude(),
                delivery.getRating(),
                delivery.isRated(),
                null,
                null
        );
    }

    private byte[] generateReceiptPdf(DeliveryRequest delivery) {
        // Implement PDF generation logic here
        // You can use libraries like iText or Apache PDFBox
        // For now, return empty byte array
        return new byte[0];
    }
}