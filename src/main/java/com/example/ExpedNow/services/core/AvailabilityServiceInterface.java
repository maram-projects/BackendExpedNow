package com.example.ExpedNow.services.core;

import com.example.ExpedNow.dto.AvailabilityDTO;
import com.example.ExpedNow.models.AvailabilitySchedule;
import org.springframework.security.access.AccessDeniedException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public interface AvailabilityServiceInterface {
    AvailabilityDTO getScheduleForUser(String userId);
    boolean isUserAvailableAt(String userId, DayOfWeek day, LocalTime time);
    boolean isUserAvailableAt(String userId, LocalDate date, LocalTime time);
    boolean isUserAvailableAt(String userId, LocalDateTime dateTime);
    AvailabilitySchedule setDayAvailability(String userId, DayOfWeek day, boolean isWorking, LocalTime startTime, LocalTime endTime);
    AvailabilitySchedule setDateAvailability(String userId, LocalDate date, boolean isWorking, LocalTime startTime, LocalTime endTime);
    AvailabilitySchedule setDateRangeAvailability(String userId, LocalDate startDate, LocalDate endDate, boolean isWorking, LocalTime startTime, LocalTime endTime);
    List<String> findAvailableDeliveryPersonsAt(LocalDateTime dateTime);

    // Add the missing methods that are implemented in AvailabilityServiceImpl but not declared in this interface
    AvailabilitySchedule generateMonthlyScheduleFromWeeklyPattern(String userId, LocalDate startDate, LocalDate endDate);
    AvailabilitySchedule setWeekdaysInRangeAvailability(String userId, LocalDate startDate, LocalDate endDate, Set<DayOfWeek> daysOfWeek, boolean isWorking, LocalTime startTime, LocalTime endTime);
    AvailabilitySchedule copyMonthlyAvailability(String userId, LocalDate sourceMonthStart, LocalDate targetMonthStart);
    AvailabilitySchedule clearAllMonthlySchedule(String userId);
    AvailabilitySchedule saveSchedule(AvailabilityDTO scheduleDTO, boolean isAdmin) throws AccessDeniedException;    /**
     * Check if a user has an existing availability schedule
     * @param userId the user ID to check
     * @return true if the user has a meaningful schedule, false otherwise
     */
    boolean hasExistingSchedule(String userId);
}