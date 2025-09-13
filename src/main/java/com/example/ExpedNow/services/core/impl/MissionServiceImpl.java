package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.*;
import com.example.ExpedNow.models.enums.BonusStatus;
import com.example.ExpedNow.repositories.*;
import com.example.ExpedNow.services.core.DeliveryAssignmentServiceInterface;
import com.example.ExpedNow.services.core.MissionServiceInterface;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
@Transactional
public class MissionServiceImpl implements MissionServiceInterface {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MissionServiceImpl.class);
    private final MissionRepository missionRepository;
    private final DeliveryReqRepository deliveryReqRepository;
    private final UserRepository userRepository;
    private final DeliveryAssignmentServiceInterface deliveryAssignmentService;
    private final BonusService bonusService; // Add this dependency

    public MissionServiceImpl(MissionRepository missionRepository,
                              DeliveryReqRepository deliveryReqRepository,
                              UserRepository userRepository,
                              DeliveryAssignmentServiceInterface deliveryAssignmentService,
                              BonusService bonusService) { // Add bonusService parameter
        this.missionRepository = missionRepository;
        this.deliveryReqRepository = deliveryReqRepository;
        this.userRepository = userRepository;
        this.deliveryAssignmentService = deliveryAssignmentService;
        this.bonusService = bonusService;
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

        // Update linked delivery request status
        updateDeliveryStatus(mission.getDeliveryRequest(), DeliveryRequest.DeliveryReqStatus.DELIVERED);
        mission.getDeliveryRequest().setCompletedAt(LocalDateTime.now());

        Mission savedMission = missionRepository.save(mission);

        String deliveryPersonId = mission.getDeliveryPerson().getId();

        // Check for milestone bonus after completing mission
        try {
            checkAndCreateMilestoneBonus(deliveryPersonId);
        } catch (Exception e) {
            logger.error("Failed to check milestone bonus for delivery person {}: {}", deliveryPersonId, e.getMessage(), e);
        }

        // Automatically assign a NEW pending delivery to the same delivery person
        try {
            logger.info("Mission completed. Attempting to assign new pending delivery to delivery person: {}", deliveryPersonId);

            List<DeliveryRequest> assignedDeliveries = deliveryAssignmentService.assignPendingDeliveriesToUser(deliveryPersonId);

            if (!assignedDeliveries.isEmpty()) {
                logger.info("Successfully assigned {} new delivery(ies) to delivery person: {}",
                        assignedDeliveries.size(), deliveryPersonId);
            } else {
                logger.info("No pending deliveries available for assignment to delivery person: {}", deliveryPersonId);
            }
        } catch (Exception e) {
            logger.error("Failed to assign new delivery after mission completion: {}", e.getMessage(), e);
        }

        return savedMission;
    }

    /**
     * Check if delivery person has completed milestones and create bonus
     */
    private void checkAndCreateMilestoneBonus(String deliveryPersonId) {
        try {
            // Count completed missions for this delivery person
            long completedMissions = missionRepository.countByDeliveryPersonIdAndStatus(deliveryPersonId, "COMPLETED");

            logger.info("Delivery person {} has completed {} missions", deliveryPersonId, completedMissions);

            // Check for 10-mission milestone
            if (completedMissions > 0 && completedMissions % 10 == 0) {

                // Check if we already gave a bonus for this milestone
                String milestoneReason = "10 Mission Milestone Bonus - " + completedMissions + " missions completed";

                // Get user details for bonus
                User deliveryPerson = userRepository.findById(deliveryPersonId)
                        .orElseThrow(() -> new RuntimeException("Delivery person not found"));

                // Create milestone bonus
                Bonus milestoneBonus = new Bonus();
                milestoneBonus.setDeliveryPersonId(deliveryPersonId);
                milestoneBonus.setAmount(100.0); // $100 for 10 missions milestone
                milestoneBonus.setReason(milestoneReason);
                milestoneBonus.setDescription("Congratulations! You have completed " + completedMissions + " missions successfully!");
                milestoneBonus.setCriteria("Complete 10 missions");
                milestoneBonus.setBonusType("MILESTONE");
                milestoneBonus.setType("MILESTONE");
                milestoneBonus.setStatus(BonusStatus.CREATED); // Ready for admin to pay
                milestoneBonus.setCreatedBy("SYSTEM");
                milestoneBonus.setDeliveryPersonName(deliveryPerson.getFullName());
                milestoneBonus.setDeliveryPersonEmail(deliveryPerson.getEmail());
                milestoneBonus.setCreatedAt(LocalDateTime.now());

                bonusService.createBonus(milestoneBonus);

                logger.info("Created milestone bonus for delivery person {} after {} completed missions",
                        deliveryPersonId, completedMissions);

            } else {
                logger.debug("No milestone reached. Delivery person {} has {} completed missions",
                        deliveryPersonId, completedMissions);
            }

        } catch (Exception e) {
            logger.error("Error checking milestone bonus for delivery person {}: {}", deliveryPersonId, e.getMessage(), e);
        }
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







    // Add this method to your MissionServiceImpl class

    /**
     * Enhanced method to get all missions with client information
     */
    @Override
    public List<Mission> getAllMissions() {
        List<Mission> missions = missionRepository.findAll();

        // Enhance missions with client information
        for (Mission mission : missions) {
            if (mission.getDeliveryRequest() != null &&
                    mission.getDeliveryRequest().getClientId() != null) {

                try {
                    // Fetch client information
                    Optional<User> clientOpt = userRepository.findById(mission.getDeliveryRequest().getClientId());

                    if (clientOpt.isPresent()) {
                        User client = clientOpt.get();

                        // Since DeliveryRequest doesn't have setClient method,
                        // you have two options:

                        // Option 1: Add client info to mission notes or description
                        String clientInfo = "Client: " + client.getFullName() + " (" + client.getEmail() + ")";
                        String currentNotes = mission.getNotes();
                        if (currentNotes == null) {
                            mission.setNotes(clientInfo);
                        } else if (!currentNotes.contains("Client:")) {
                            mission.setNotes(currentNotes + " | " + clientInfo);
                        }

                        // Option 2: Store client in a transient field (see DeliveryRequest entity fix below)
                        // mission.getDeliveryRequest().setClient(client);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to fetch client info for mission {}: {}",
                            mission.getId(), e.getMessage());
                }
            }
        }

        return missions;
    }

    /**
     * Get all missions with complete client information populated
     */
    @Override
    public List<Mission> getAllMissionsWithClientInfo() {
        return List.of();
    }

    /**
     * Get missions by status (alternative to filtering in controller)
     *
     * @param status
     */
    @Override
    public List<Mission> getMissionsByStatus(String status) {
        return List.of();
    }

    /**
     * Get missions with pagination support
     *
     * @param pageable
     */
    @Override
    public Page<Mission> getAllMissionsPaginated(Pageable pageable) {
        return null;
    }

    /**
     * Get missions by date range
     *
     * @param startDate
     * @param endDate
     */
    @Override
    public List<Mission> getMissionsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return List.of();
    }

    /**
     * Get missions count by status for statistics
     */
    @Override
    public Map<String, Long> getMissionStatisticsByStatus() {
        return Map.of();
    }

    /**
     * Enhanced method to get active missions with client information
     */
    @Override
    public List<Mission> getActiveMissions() {
        List<Mission> missions = missionRepository.findByStatusIn(List.of("PENDING", "IN_PROGRESS"));

        // Enhance missions with client information
        for (Mission mission : missions) {
            if (mission.getDeliveryRequest() != null &&
                    mission.getDeliveryRequest().getClientId() != null) {

                try {
                    Optional<User> clientOpt = userRepository.findById(mission.getDeliveryRequest().getClientId());

                    if (clientOpt.isPresent()) {
                        User client = clientOpt.get();

                        // Add client info to mission notes
                        String clientInfo = "Client: " + client.getFullName() + " (" + client.getEmail() + ")";
                        String currentNotes = mission.getNotes();
                        if (currentNotes == null) {
                            mission.setNotes(clientInfo);
                        } else if (!currentNotes.contains("Client:")) {
                            mission.setNotes(currentNotes + " | " + clientInfo);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to fetch client info for active mission {}: {}",
                            mission.getId(), e.getMessage());
                }
            }
        }

        return missions;
    }

    /**
     * Enhanced method to get mission by ID with client information
     */
    @Override
    public Mission getMissionById(String missionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new RuntimeException("Mission not found with id: " + missionId));

        // Add client information
        if (mission.getDeliveryRequest() != null &&
                mission.getDeliveryRequest().getClientId() != null) {

            try {
                Optional<User> clientOpt = userRepository.findById(mission.getDeliveryRequest().getClientId());

                if (clientOpt.isPresent()) {
                    User client = clientOpt.get();

                    // Add client info to mission notes
                    String clientInfo = "Client: " + client.getFullName() + " (" + client.getEmail() + ")";
                    String currentNotes = mission.getNotes();
                    if (currentNotes == null) {
                        mission.setNotes(clientInfo);
                    } else if (!currentNotes.contains("Client:")) {
                        mission.setNotes(currentNotes + " | " + clientInfo);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to fetch client info for mission {}: {}", missionId, e.getMessage());
            }
        }

        return mission;
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

                // Check for milestone bonus when mission is completed via status update
                try {
                    String deliveryPersonId = mission.getDeliveryPerson().getId();
                    checkAndCreateMilestoneBonus(deliveryPersonId);
                } catch (Exception e) {
                    logger.error("Failed to check milestone bonus after status update: {}", e.getMessage(), e);
                }

                // Auto-assign new deliveries when mission is completed via status update
                try {
                    String deliveryPersonId = mission.getDeliveryPerson().getId();
                    logger.info("Mission status updated to COMPLETED. Attempting to assign new delivery to person: {}", deliveryPersonId);

                    List<DeliveryRequest> assignedDeliveries = deliveryAssignmentService.assignPendingDeliveriesToUser(deliveryPersonId);

                    if (!assignedDeliveries.isEmpty()) {
                        logger.info("Successfully assigned {} new delivery(ies) after status update", assignedDeliveries.size());
                    }
                } catch (Exception e) {
                    logger.error("Failed to assign new delivery after status update: {}", e.getMessage(), e);
                }
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

    // ====== Private Methods ======

    private void updateDeliveryStatus(DeliveryRequest delivery, DeliveryRequest.DeliveryReqStatus newStatus) {
        delivery.setStatus(newStatus);
        deliveryReqRepository.save(delivery);
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