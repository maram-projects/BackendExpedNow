package com.example.ExpedNow.models;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Document(collection = "Schedule")
public class AvailabilitySchedule {
    @Indexed
    private String id;
    private String userId;
    private Map<DayOfWeek, DaySchedule> weeklySchedule;
    // Add monthly schedule mapping date to schedule
    private Map<LocalDate, DaySchedule> monthlySchedule;

    // Default constructor
    public AvailabilitySchedule() {
        this.weeklySchedule = new HashMap<>();
        this.monthlySchedule = new TreeMap<>(); // TreeMap to keep dates ordered
    }

    // Constructor with userId
    public AvailabilitySchedule(String userId) {
        this.userId = userId;
        this.weeklySchedule = new HashMap<>();
        this.monthlySchedule = new TreeMap<>();

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

    // New getters and setters for monthly schedule
    public Map<LocalDate, DaySchedule> getMonthlySchedule() {
        return monthlySchedule;
    }

    public void setMonthlySchedule(Map<LocalDate, DaySchedule> monthlySchedule) {
        this.monthlySchedule = monthlySchedule;
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

    // Check if user is available on a specific date and time
    public boolean isAvailable(LocalDate date, LocalTime time) {
        // First check if there's a specific schedule for this date
        DaySchedule specificSchedule = monthlySchedule.get(date);

        if (specificSchedule != null) {
            // If there's a specific schedule for this date, use it
            if (!specificSchedule.isWorking()) {
                return false;
            }

            // If working but no times specified, assume available all day
            if (specificSchedule.getStartTime() == null || specificSchedule.getEndTime() == null) {
                return true;
            }

            // Check if the time is within the specific working hours
            return !time.isBefore(specificSchedule.getStartTime()) && !time.isAfter(specificSchedule.getEndTime());
        } else {
            // If no specific schedule for this date, fall back to weekly schedule
            return isAvailable(date.getDayOfWeek(), time);
        }
    }

    // Inner class for day schedule (unchanged)
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
