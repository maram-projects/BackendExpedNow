package com.example.ExpedNow.services.core;


import com.example.ExpedNow.models.DeliveryRequest;

import java.util.List;

public interface DeliveryServiceInterface {

    DeliveryRequest createDelivery(DeliveryRequest delivery);

    List<DeliveryRequest> getAllDeliveries();

    List<DeliveryRequest> getClientDeliveries(String clientId);

    List<DeliveryRequest> getPendingDeliveries();

    DeliveryRequest updateDeliveryStatus(String id, DeliveryRequest.DeliveryReqStatus status);

    void cancelDelivery(String id);

    DeliveryRequest getDeliveryById(String id);
}