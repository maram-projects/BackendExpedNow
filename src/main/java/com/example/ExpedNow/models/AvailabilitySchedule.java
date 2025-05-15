// Add this helper method to AvailabilitySchedule.java if it doesn't exist already

package com.example.ExpedNow.models;

import org.springframework.data.mongodb.core.mapping.Document;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
@Document(collection = "Schedule")

public class AvailabilitySchedule {
    private String id;
    private String userId;
    private Map<DayOfWeek, DaySchedule> weeklySchedule;

    // Default constructor
    public AvailabilitySchedule() {
        this.weeklySchedule = new HashMap<>();
    }
    // Constructor with userId
    public AvailabilitySchedule(String userId) {
        this.userId = userId;
        this.weeklySchedule = new HashMap<>();

        // Initialize with default values for all days of the week
        for (DayOfWeek day : DayOfWeek.values()) {
            this.weeklySchedule.put(day, new DaySchedule(false, null, null));
        }
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Map<DayOfWeek, DaySchedule> getWeeklySchedule() {
        return weeklySchedule;
    }

    public void setWeeklySchedule(Map<DayOfWeek, DaySchedule> weeklySchedule) {
        this.weeklySchedule = weeklySchedule;
    }

    // Check if user is available at a specific day and time
    public boolean isAvailable(DayOfWeek day, LocalTime time) {
        DaySchedule schedule = weeklySchedule.get(day);

        // If no schedule exists for this day or not working, return false
        if (schedule == null || !schedule.isWorking()) {
            return false;
        }

        // If working but no times specified, assume available all day
        if (schedule.getStartTime() == null || schedule.getEndTime() == null) {
            return true;
        }

        // Check if the time is within the working hours
        return !time.isBefore(schedule.getStartTime()) && !time.isAfter(schedule.getEndTime());
    }

    // Inner class for day schedule
    public static class DaySchedule {
        private boolean working;
        private LocalTime startTime;
        private LocalTime endTime;

        // Default constructor
        public DaySchedule() {
        }

        // Constructor with parameters
        public DaySchedule(boolean working, LocalTime startTime, LocalTime endTime) {
            this.working = working;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        // Getters and setters
        public boolean isWorking() {
            return working;
        }

        public void setWorking(boolean working) {
            this.working = working;
        }

        public LocalTime getStartTime() {
            return startTime;
        }

        public void setStartTime(LocalTime startTime) {
            this.startTime = startTime;
        }

        public LocalTime getEndTime() {
            return endTime;
        }

        public void setEndTime(LocalTime endTime) {
            this.endTime = endTime;
        }
    }
}