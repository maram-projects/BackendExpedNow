package com.example.ExpedNow.services.core;

import com.example.ExpedNow.models.Delivery;

import java.util.List;

public interface DeliveryAssignmentServiceInterface {
    /**
     * Assigns a delivery to the closest available delivery person
     *
     * @param deliveryId the delivery to assign
     * @return the updated delivery with assignment information
     */
    Delivery assignDelivery(String deliveryId);

    /**
     * Updates the status of a delivery
     */
    Delivery updateDeliveryStatus(String deliveryId, Delivery.DeliveryStatus newStatus, String deliveryPersonId);

    /**
     * Gets all deliveries assigned to a specific delivery person
     */
    List<Delivery> getDeliveriesForPerson(String deliveryPersonId);

    /**
     * Gets all pending deliveries that need assignment
     */
    List<Delivery> getPendingDeliveries();
}