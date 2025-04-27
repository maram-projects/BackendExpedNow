package com.example.ExpedNow.services.core;

import com.example.ExpedNow.models.Mission;
import java.util.List;

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
}