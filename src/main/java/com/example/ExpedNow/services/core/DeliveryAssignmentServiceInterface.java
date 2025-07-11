package com.example.ExpedNow.services.core;

import com.example.ExpedNow.models.DeliveryRequest;

import java.util.List;

public interface DeliveryAssignmentServiceInterface {
    List<DeliveryRequest> assignPendingDeliveriesToUser(String userId);

    /**
     * Assigns a delivery to the closest available delivery person
     *
     * @param deliveryId the delivery to assign
     * @return the updated delivery with assignment information
     */
    DeliveryRequest assignDelivery(String deliveryId);

    /**
     * Updates the status of a delivery
     */
    DeliveryRequest updateDeliveryStatus(String deliveryId, DeliveryRequest.DeliveryReqStatus newStatus, String deliveryPersonId);

    /**
     * Gets all deliveries assigned to a specific delivery person
     */
    List<DeliveryRequest> getDeliveriesForPerson(String deliveryPersonId);

    /**
     * Gets all pending deliveries that need assignment
     */
    List<DeliveryRequest> getPendingDeliveries();

    List<DeliveryRequest> getAssignedDeliveries();

    List<DeliveryRequest> getDeliveriesByClientId(String clientId);

    List<DeliveryRequest> getDeliveriesByDeliveryPersonId(String deliveryPersonId);
}