package com.example.ExpedNow.services.core;

import com.example.ExpedNow.models.Mission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface MissionServiceInterface {
    Mission createMission(String deliveryId, String deliveryPersonId);
    Mission startMission(String missionId);
    Mission completeMission(String missionId);
    Mission cancelMission(String missionId);
    List<Mission> getMissionsByDeliveryPerson(String deliveryPersonId);
    List<Mission> getActiveMissions();


    Mission getMissionById(String missionId);
    Mission updateMissionStatus(String missionId, String status);
    Mission addMissionNotes(String missionId, String notes);


    /**
     * Get ALL missions regardless of status (for admin dashboard)
     */
    List<Mission> getAllMissions();

    /**
     * Get all missions with complete client information populated
     */
    List<Mission> getAllMissionsWithClientInfo();

    /**
     * Get missions by status (alternative to filtering in controller)
     */
    List<Mission> getMissionsByStatus(String status);

    /**
     * Get missions with pagination support
     */
    Page<Mission> getAllMissionsPaginated(Pageable pageable);

    /**
     * Get missions by date range
     */
    List<Mission> getMissionsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Get missions count by status for statistics
     */
    Map<String, Long> getMissionStatisticsByStatus();
}