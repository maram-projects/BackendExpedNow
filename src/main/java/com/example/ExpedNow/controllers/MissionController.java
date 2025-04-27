package com.example.ExpedNow.controllers;

import com.example.ExpedNow.models.Mission;
import com.example.ExpedNow.services.core.MissionServiceInterface;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/missions")
public class MissionController {

    private final MissionServiceInterface missionService;

    public MissionController(MissionServiceInterface missionService) {
        this.missionService = missionService;
    }

    @PostMapping
    public Mission createMission(
            @RequestParam String deliveryId,
            @RequestParam String deliveryPersonId) {
        return missionService.createMission(deliveryId, deliveryPersonId);
    }

    @PostMapping("/{missionId}/start")
    public ResponseEntity<?> startMission(@PathVariable String missionId) {
        try {
            Mission mission = missionService.startMission(missionId);
            return ResponseEntity.ok(mission);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error starting mission: " + e.getMessage());
        }
    }

    @PostMapping("/{missionId}/complete")
    public Mission completeMission(@PathVariable String missionId) {
        return missionService.completeMission(missionId);
    }

    @PostMapping("/{missionId}/cancel")
    public Mission cancelMission(@PathVariable String missionId) {
        return missionService.cancelMission(missionId);
    }

    @GetMapping("/delivery-person/{deliveryPersonId}")
    public List<Mission> getMissionsByDeliveryPerson(
            @PathVariable String deliveryPersonId) {
        return missionService.getMissionsByDeliveryPerson(deliveryPersonId);
    }

    @GetMapping("/active")
    public List<Mission> getActiveMissions() {
        return missionService.getActiveMissions();
    }

    @GetMapping("/{missionId}")
    public Mission getMissionById(@PathVariable String missionId) {
        return missionService.getMissionById(missionId);
    }

    @PatchMapping("/{missionId}/status")
    public Mission updateMissionStatus(
            @PathVariable String missionId,
            @RequestParam String status) {
        return missionService.updateMissionStatus(missionId, status);
    }

    @PatchMapping("/{missionId}/notes")
    public Mission addMissionNotes(
            @PathVariable String missionId,
            @RequestBody String notes) {
        return missionService.addMissionNotes(missionId, notes);
    }
}