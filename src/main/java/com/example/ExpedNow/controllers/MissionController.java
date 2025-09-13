package com.example.ExpedNow.controllers;

import ch.qos.logback.classic.Logger;
import com.example.ExpedNow.models.Mission;
import com.example.ExpedNow.services.core.MissionServiceInterface;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


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

    // ADD THIS NEW ENDPOINT FOR ADMIN DASHBOARD - GET ALL MISSIONS
    @GetMapping("/all")
    public ResponseEntity<List<Mission>> getAllMissions() {
        try {
            // This should return ALL missions regardless of status
            List<Mission> allMissions = missionService.getAllMissions();
            return ResponseEntity.ok(allMissions);
        } catch (Exception e) {
            Logger logger = null;
            logger.error("Error fetching all missions: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
        }
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

    // UPDATED STATISTICS ENDPOINT
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getMissionStatistics() {
        try {
            // Use getAllMissions instead of getActiveMissions for complete statistics
            List<Mission> allMissions = missionService.getAllMissions();

            Map<String, Object> stats = new HashMap<>();

            // Calculate statistics from ALL missions
            long total = allMissions.size();
            long pending = allMissions.stream().filter(m -> "PENDING".equals(m.getStatus())).count();
            long inProgress = allMissions.stream().filter(m -> "IN_PROGRESS".equals(m.getStatus())).count();
            long completed = allMissions.stream().filter(m -> "COMPLETED".equals(m.getStatus())).count();
            long cancelled = allMissions.stream().filter(m -> "CANCELLED".equals(m.getStatus())).count();

            // Calculate average duration for completed missions
            double averageDuration = allMissions.stream()
                    .filter(m -> "COMPLETED".equals(m.getStatus()) && m.getStartTime() != null && m.getEndTime() != null)
                    .mapToLong(m -> Duration.between(m.getStartTime(), m.getEndTime()).toMillis())
                    .average()
                    .orElse(0.0);

            stats.put("total", total);
            stats.put("pending", pending);
            stats.put("inProgress", inProgress);
            stats.put("completed", completed);
            stats.put("cancelled", cancelled);
            stats.put("averageDuration", averageDuration);

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            Map<String, Object> errorStats = new HashMap<>();
            errorStats.put("total", 0);
            errorStats.put("pending", 0);
            errorStats.put("inProgress", 0);
            errorStats.put("completed", 0);
            errorStats.put("cancelled", 0);
            errorStats.put("averageDuration", 0.0);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorStats);
        }
    }

    // UPDATED ENDPOINT FOR GETTING MISSIONS BY STATUS
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Mission>> getMissionsByStatus(@PathVariable String status) {
        try {
            // Validate status
            if (!List.of("PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED").contains(status)) {
                return ResponseEntity.badRequest().body(null);
            }

            // Get all missions and filter by status
            List<Mission> allMissions = missionService.getAllMissions();
            List<Mission> filteredMissions = allMissions.stream()
                    .filter(m -> status.equals(m.getStatus()))
                    .toList();

            return ResponseEntity.ok(filteredMissions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // ADD ENDPOINT FOR GETTING MISSIONS WITH CLIENT INFO
    @GetMapping("/with-client-info")
    public ResponseEntity<List<Mission>> getMissionsWithClientInfo() {
        try {
            List<Mission> missions = missionService.getAllMissionsWithClientInfo();
            return ResponseEntity.ok(missions);
        } catch (Exception e) {
            Logger logger = null;
            logger.error("Error fetching missions with client info: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
        }
    }
}