package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.*;
import com.example.ExpedNow.repositories.*;
import com.example.ExpedNow.services.core.MissionServiceInterface;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional; // أضف هذا السطر
@Service
@Primary
@Transactional
public class MissionServiceImpl implements MissionServiceInterface {

    private final MissionRepository missionRepository;
    private final DeliveryReqRepository deliveryReqRepository;
    private final UserRepository userRepository;

    public MissionServiceImpl(MissionRepository missionRepository,
                              DeliveryReqRepository deliveryReqRepository,
                              UserRepository userRepository) {
        this.missionRepository = missionRepository;
        this.deliveryReqRepository = deliveryReqRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Mission createMission(String deliveryId, String deliveryPersonId) {
        DeliveryRequest delivery = deliveryReqRepository.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery request not found"));

        // تحقق من أن المندوب هو المعين للطلب
        if (!delivery.getDeliveryPersonId().equals(deliveryPersonId)) {
            throw new RuntimeException("Delivery person not assigned to this request");
        }

        // يجب أن تكون الحالة ASSIGNED فقط لإنشاء المهمة
        if (delivery.getStatus() != DeliveryRequest.DeliveryReqStatus.ASSIGNED) {
            throw new RuntimeException("Cannot create mission for delivery with status: " + delivery.getStatus());
        }



        User deliveryPerson = userRepository.findById(deliveryPersonId)
                .orElseThrow(() -> new RuntimeException("Delivery person not found"));

        // تحقق إذا كانت هناك مهمة نشطة لنفس الطلب
        missionRepository.findByDeliveryRequestId(deliveryId)
                .ifPresent(m -> {
                    throw new RuntimeException("Mission already exists for this delivery");
                });

        Mission mission = new Mission();
        mission.setDeliveryRequest(delivery);
        mission.setDeliveryPerson(deliveryPerson);
        mission.setStatus("PENDING");
        mission.setStartTime(LocalDateTime.now());

        // تحديث حالة الطلب إلى APPROVED عند إنشاء المهمة
        updateDeliveryStatus(delivery, DeliveryRequest.DeliveryReqStatus.APPROVED);
        delivery.setDeliveryPersonId(deliveryPersonId);
        deliveryReqRepository.save(delivery);

        return missionRepository.save(mission);
    }
    @Override
    public Mission startMission(String missionId) {
        Mission mission = getMissionById(missionId);

        validateMissionStatus(mission, "PENDING", "start");

        mission.setStatus("IN_PROGRESS");
        mission.setStartTime(LocalDateTime.now());

        // تحديث حالة الطلب المرتبط
        DeliveryRequest deliveryRequest = mission.getDeliveryRequest();
        deliveryRequest.setStatus(DeliveryRequest.DeliveryReqStatus.IN_TRANSIT);
        deliveryRequest.setStartedAt(LocalDateTime.now());
        deliveryReqRepository.save(deliveryRequest);

        return missionRepository.save(mission);
    }

    @Override
    public Mission completeMission(String missionId) {
        Mission mission = getMissionById(missionId);

        validateMissionStatus(mission, "IN_PROGRESS", "complete");

        mission.setStatus("COMPLETED");
        mission.setEndTime(LocalDateTime.now());

        // تحديث حالة الطلب المرتبط
        updateDeliveryStatus(mission.getDeliveryRequest(), DeliveryRequest.DeliveryReqStatus.DELIVERED);
        mission.getDeliveryRequest().setCompletedAt(LocalDateTime.now());

        return missionRepository.save(mission);
    }

    @Override
    public Mission cancelMission(String missionId) {
        Mission mission = getMissionById(missionId);

        mission.setStatus("CANCELLED");
        mission.setEndTime(LocalDateTime.now());

        // تحديث حالة الطلب المرتبط
        updateDeliveryStatus(mission.getDeliveryRequest(), DeliveryRequest.DeliveryReqStatus.CANCELLED);

        return missionRepository.save(mission);
    }

    @Override
    public List<Mission> getMissionsByDeliveryPerson(String deliveryPersonId) {
        return missionRepository.findByDeliveryPersonId(deliveryPersonId);
    }

    @Override
    public List<Mission> getActiveMissions() {
        return missionRepository.findByStatusIn(List.of("PENDING", "IN_PROGRESS"));
    }

    @Override
    public Mission getMissionById(String missionId) {
        return missionRepository.findById(missionId)
                .orElseThrow(() -> new RuntimeException("Mission not found with id: " + missionId));
    }

    @Override
    public Mission updateMissionStatus(String missionId, String status) {
        Mission mission = getMissionById(missionId);

        if (!List.of("PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED").contains(status)) {
            throw new RuntimeException("Invalid status. Allowed values: PENDING, IN_PROGRESS, COMPLETED, CANCELLED");
        }

        // التحقق من تدفق الحالات المسموح به
        validateStatusTransition(mission.getStatus(), status);

        mission.setStatus(status);

        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            mission.setEndTime(LocalDateTime.now());

            // تحديث حالة الطلب المرتبط إذا لزم الأمر
            if ("COMPLETED".equals(status)) {
                updateDeliveryStatus(mission.getDeliveryRequest(), DeliveryRequest.DeliveryReqStatus.DELIVERED);
            } else if ("CANCELLED".equals(status)) {
                updateDeliveryStatus(mission.getDeliveryRequest(), DeliveryRequest.DeliveryReqStatus.CANCELLED);
            }
        }

        return missionRepository.save(mission);
    }

    @Override
    public Mission addMissionNotes(String missionId, String notes) {
        Mission mission = getMissionById(missionId);
        mission.setNotes(notes);
        return missionRepository.save(mission);
    }

    // ====== Methods privées ======

    private void updateDeliveryStatus(DeliveryRequest delivery, DeliveryRequest.DeliveryReqStatus newStatus) {
        delivery.setStatus(newStatus);
        deliveryReqRepository.save(delivery); // تأكيد الحفظ
    }

    private void validateMissionStatus(Mission mission, String expectedStatus, String action) {
        if (!expectedStatus.equals(mission.getStatus())) {
            throw new RuntimeException(
                    String.format("Cannot %s mission from current status: %s", action, mission.getStatus())
            );
        }
    }

    private void validateStatusTransition(String currentStatus, String newStatus) {
        switch (currentStatus) {
            case "PENDING":
                if (!List.of("IN_PROGRESS", "CANCELLED").contains(newStatus)) {
                    throw new RuntimeException("Invalid status transition from PENDING to " + newStatus);
                }
                break;
            case "IN_PROGRESS":
                if (!List.of("COMPLETED", "CANCELLED").contains(newStatus)) {
                    throw new RuntimeException("Invalid status transition from IN_PROGRESS to " + newStatus);
                }
                break;
            case "COMPLETED":
            case "CANCELLED":
                throw new RuntimeException("Cannot change status from " + currentStatus);
            default:
                throw new RuntimeException("Unknown current status: " + currentStatus);
        }
    }


}