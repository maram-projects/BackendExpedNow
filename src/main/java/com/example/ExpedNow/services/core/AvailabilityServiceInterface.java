package com.example.ExpedNow.services.core;

import com.example.ExpedNow.dto.AvailabilityDTO;
import com.example.ExpedNow.models.AvailabilitySchedule;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public interface AvailabilityServiceInterface {

    // Create or update a user's availability schedule
    AvailabilitySchedule saveSchedule(AvailabilityDTO scheduleDTO);

    // Get a user's availability schedule
    //AvailabilityDTO getScheduleForUser(String userId);

    // Check if a user is available at a specific time
    boolean isUserAvailableAt(String userId, LocalDateTime dateTime);

    // Set availability for specific day
    AvailabilitySchedule setDayAvailability(String userId, DayOfWeek day,
                                            boolean isWorking, LocalTime startTime, LocalTime endTime);
    AvailabilityDTO getScheduleForUser(String userId);

    // Find all available delivery persons at a specific time
    List<String> findAvailableDeliveryPersonsAt(LocalDateTime dateTime);

    boolean isUserAvailableAt(String id, DayOfWeek dayOfWeek, LocalTime time);
}