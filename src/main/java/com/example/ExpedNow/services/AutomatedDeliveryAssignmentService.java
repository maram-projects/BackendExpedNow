package com.example.ExpedNow.services;

import com.example.ExpedNow.models.Delivery;
import com.example.ExpedNow.repositories.DeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AutomatedDeliveryAssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(AutomatedDeliveryAssignmentService.class);

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryAssignmentService deliveryAssignmentService;

    /**
     * Automatically assigns pending deliveries to available delivery persons
     * This method runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes in milliseconds
    public void assignPendingDeliveries() {
        logger.info("Starting automated delivery assignment process");
        List<Delivery> pendingDeliveries = deliveryRepository.findByStatus(Delivery.DeliveryStatus.PENDING);

        if (pendingDeliveries.isEmpty()) {
            logger.info("No pending deliveries found");
            return;
        }

        logger.info("Found {} pending deliveries to assign", pendingDeliveries.size());
        int assignedCount = 0;

        for (Delivery delivery : pendingDeliveries) {
            try {
                deliveryAssignmentService.assignDelivery(delivery.getId());
                assignedCount++;
                logger.info("Successfully assigned delivery ID: {}", delivery.getId());
            } catch (Exception e) {
                logger.error("Failed to assign delivery ID: {}. Error: {}", delivery.getId(), e.getMessage());
            }
        }

        logger.info("Completed assignment process. Assigned {} out of {} deliveries",
                assignedCount, pendingDeliveries.size());
    }

}