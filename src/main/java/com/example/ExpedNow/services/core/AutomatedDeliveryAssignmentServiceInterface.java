package com.example.ExpedNow.services.core;

/**
 * Interface for automated delivery assignment functionality
 */
public interface AutomatedDeliveryAssignmentServiceInterface {

    /**
     * Automatically assigns pending deliveries to available delivery persons
     * This method runs on a scheduled basis
     */
    void assignPendingDeliveries();
}