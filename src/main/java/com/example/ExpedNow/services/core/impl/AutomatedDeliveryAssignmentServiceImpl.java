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
    @Scheduled(fixedRate = 300000) // 5 دقائق بالميلي ثانية
    public void assignPendingDeliveries() {
        logger.info("🔄 بدء عملية تعيين التوصيل الآلي...");

        // الحصول على طلبات التوصيل المعلقة بدون موصل معين
        List<DeliveryRequest> pendingDeliveries = deliveryRepository.findByStatusAndDeliveryPersonIdIsNull(
                DeliveryRequest.DeliveryReqStatus.PENDING);

        if (pendingDeliveries.isEmpty()) {
            logger.info("✅ لم يتم العثور على طلبات معلقة. لا شيء للتعيين.");
            return;
        }

        logger.info("📦 تم العثور على {} طلبات معلقة للتعيين.", pendingDeliveries.size());
        int assignedCount = 0;

        for (DeliveryRequest delivery : pendingDeliveries) {
            try {
                DeliveryRequest assignedDelivery = deliveryAssignmentService.assignDelivery(delivery.getId());
                if (assignedDelivery.getDeliveryPersonId() != null) {
                    assignedCount++;
                    logger.info("✔️ تم تعيين طلب التوصيل بنجاح معرف: {} إلى موصل معرف: {}",
                            assignedDelivery.getId(), assignedDelivery.getDeliveryPersonId());
                } else {
                    logger.warn("⚠️ لا يوجد موصل متاح لطلب التوصيل معرف: {}", delivery.getId());
                }
            } catch (Exception e) {
                logger.error("❌ فشل في تعيين طلب التوصيل معرف: {}. خطأ: {}", delivery.getId(), e.getMessage());
            }
        }

        logger.info("🏁 اكتملت عملية التعيين. تم تعيين {} من أصل {} طلبات بنجاح.",
                assignedCount, pendingDeliveries.size());
    }}