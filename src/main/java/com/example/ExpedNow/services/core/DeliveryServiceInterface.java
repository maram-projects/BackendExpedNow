package com.example.ExpedNow.services.core;

import com.example.ExpedNow.dto.DeliveryPersonPerformanceDTO;
import com.example.ExpedNow.dto.DeliveryResponseDTO;
import com.example.ExpedNow.dto.DetailedRatingResponse;
import com.example.ExpedNow.dto.RatingStatistics;
import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.Vehicle;
import com.example.ExpedNow.models.enums.PaymentStatus;
import com.example.ExpedNow.models.enums.Role;

import java.time.LocalDateTime;
import java.util.List;

public interface DeliveryServiceInterface {

    DeliveryRequest createDelivery(DeliveryRequest delivery);

    List<DeliveryRequest> getAllDeliveries();

    List<DeliveryRequest> getClientDeliveries(String clientId);


    List<DeliveryRequest> getPendingDeliveries();

    DeliveryRequest updateDeliveryStatus(String id, DeliveryRequest.DeliveryReqStatus status);

    void cancelDelivery(String id); // للإدارة

    void cancelDelivery(String id, String clientId); // للعملاء

    DeliveryRequest getDeliveryById(String id);

    DeliveryRequest resetDeliveryAssignment(String deliveryId, String deliveryPersonId);

    List<DeliveryResponseDTO> getAssignedPendingDeliveries(String deliveryPersonId);

    List<DeliveryResponseDTO> getDeliveryHistory(String deliveryPersonId);
    DeliveryRequest updateDeliveryPaymentStatus(String deliveryId, String paymentId, PaymentStatus paymentStatus);

    // Add this method
    void rateDelivery(String deliveryId, double rating, String clientId);

    List<DeliveryRequest> findByStatusAndRatedAtAfter(DeliveryRequest.DeliveryReqStatus status, LocalDateTime date);

    List<DeliveryRequest> findByDeliveryPersonIdAndStatusAndRatedAtAfter(
            String deliveryPersonId,
            DeliveryRequest.DeliveryReqStatus status,
            LocalDateTime date);

    List<DeliveryRequest> findByClientIdAndRatedOrderByCreatedAtDesc(String clientId, boolean rated);

    List<DeliveryRequest> findByRated(boolean rated);

    List<DeliveryRequest> findByDeliveryPersonIdAndRated(String deliveryPersonId, boolean rated);

    List<DetailedRatingResponse> getAllRatings();

    RatingStatistics getRatingStatistics(String deliveryPersonId, int days);

    List<DetailedRatingResponse> getDeliveryPersonRatings(String deliveryPersonId);

    DeliveryResponseDTO getDeliveryWithDetails(String deliveryId);

    // In DeliveryServiceImpl.java
    int countTodayCompletedDeliveries(String deliveryPersonId);

    List<DeliveryPersonPerformanceDTO> getDeliveryPersonPerformanceStats();

    List<User> findByRoles(Role role);
}