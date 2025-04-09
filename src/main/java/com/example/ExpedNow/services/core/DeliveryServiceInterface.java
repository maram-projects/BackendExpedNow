package com.example.ExpedNow.services.core;

import com.example.ExpedNow.models.Delivery;

import java.util.List;

public interface DeliveryServiceInterface {

    Delivery createDelivery(Delivery delivery);

    List<Delivery> getAllDeliveries();

    List<Delivery> getClientDeliveries(String clientId);

    List<Delivery> getPendingDeliveries();

    Delivery updateDeliveryStatus(String id, Delivery.DeliveryStatus status);

    void cancelDelivery(String id);

    Delivery getDeliveryById(String id);
}