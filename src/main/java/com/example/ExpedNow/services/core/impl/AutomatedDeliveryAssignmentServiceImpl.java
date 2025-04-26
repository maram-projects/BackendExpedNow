package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.DeliveryRequest;
import com.example.ExpedNow.repositories.DeliveryReqRepository;
import com.example.ExpedNow.services.core.AutomatedDeliveryAssignmentServiceInterface;
import com.example.ExpedNow.services.core.DeliveryAssignmentServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
public class AutomatedDeliveryAssignmentServiceImpl implements AutomatedDeliveryAssignmentServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(AutomatedDeliveryAssignmentServiceImpl.class);

    @Autowired
    private DeliveryReqRepository deliveryRepository;

    @Autowired
    private DeliveryAssignmentServiceInterface deliveryAssignmentService;

    /**
     * Automatically assigns pending deliveries to available delivery persons
     * This method runs every 5 minutes
     */
    @Override
    @Scheduled(fixedRate = 300000) // 5 minutes in milliseconds
    public void assignPendingDeliveries() {
        logger.info("🔄 Starting automated delivery assignment process...");

        List<DeliveryRequest> pendingDeliveries = deliveryRepository.findByStatus(DeliveryRequest.DeliveryReqStatus.PENDING);

        if (pendingDeliveries.isEmpty()) {
            logger.info("✅ No pending deliveries found. Nothing to assign.");
            return;
        }

        logger.info("📦 Found {} pending deliveries to assign.", pendingDeliveries.size());
        int assignedCount = 0;

        for (DeliveryRequest delivery : pendingDeliveries) {
            try {
                DeliveryRequest assignedDelivery = deliveryAssignmentService.assignDelivery(delivery.getId());
                if (assignedDelivery.getDeliveryPersonId() != null) {
                    assignedCount++;
                    logger.info("✔️ Successfully assigned delivery ID: {} to delivery person ID: {}",
                            assignedDelivery.getId(), assignedDelivery.getDeliveryPersonId());
                } else {
                    logger.warn("⚠️ No delivery person available for delivery ID: {}", delivery.getId());
                }
            } catch (Exception e) {
                logger.error("❌ Failed to assign delivery ID: {}. Error: {}", delivery.getId(), e.getMessage());
            }
        }

        logger.info("🏁 Assignment process completed. Successfully assigned {} out of {} deliveries.",
                assignedCount, pendingDeliveries.size());
    }
}
