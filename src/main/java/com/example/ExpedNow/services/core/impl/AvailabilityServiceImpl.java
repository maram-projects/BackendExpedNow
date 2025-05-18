
package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.dto.AvailabilityDTO;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import com.example.ExpedNow.models.AvailabilitySchedule;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.repositories.AvailabilityRepository;
import com.example.ExpedNow.repositories.UserRepository;
import com.example.ExpedNow.services.core.AvailabilityServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Primary
public class AvailabilityServiceImpl implements AvailabilityServiceInterface {
    private static final Logger logger = LoggerFactory.getLogger(AvailabilityServiceImpl.class);

    private final AvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;

    @Autowired
    public AvailabilityServiceImpl(AvailabilityRepository availabilityRepository, UserRepository userRepository) {
        this.availabilityRepository = availabilityRepository;
        this.userRepository = userRepository;
    }

    @Override
    public AvailabilitySchedule saveSchedule(AvailabilityDTO scheduleDTO) {
        // Log the incoming user ID for debugging
        logger.info("Saving schedule for user ID: {}", scheduleDTO.getUserId());

        // Validate schedule
        if (scheduleDTO.getUserId() == null || scheduleDTO.getUserId().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }

        // Check for valid time ranges in weekly schedule
        if (scheduleDTO.getWeeklySchedule() != null) {
            scheduleDTO.getWeeklySchedule().forEach((day, schedule) -> {
                if (schedule.isWorking()) {
                    if (schedule.getStartTime() == null || schedule.getEndTime() == null) {
                        throw new IllegalArgumentException("Start and end times required for working days");
                    }
                    if (!schedule.getStartTime().isBefore(schedule.getEndTime())) {
                        throw new IllegalArgumentException("Invalid time range for " + day);
                    }
                }
            });
        }

        // Check for valid time ranges in monthly schedule
        if (scheduleDTO.getMonthlySchedule() != null) {
            scheduleDTO.getMonthlySchedule().forEach((date, schedule) -> {
                if (schedule.isWorking()) {
                    if (schedule.getStartTime() == null || schedule.getEndTime() == null) {
                        throw new IllegalArgumentException("Start and end times required for working dates");
                    }
                    if (!schedule.getStartTime().isBefore(schedule.getEndTime())) {
                        throw new IllegalArgumentException("Invalid time range for " + date);
                    }
                }
            });
        }

        // Try to find user by email first (if userId looks like an email)
        if (scheduleDTO.getUserId().contains("@")) {
            Optional<User> userByEmail = userRepository.findByEmail(scheduleDTO.getUserId());
            if (userByEmail.isPresent()) {
                // If user is found by email, use their ID instead
                scheduleDTO.setUserId(userByEmail.get().getId());
            } else if (!userRepository.existsById(scheduleDTO.getUserId())) {
                throw new ResourceNotFoundException("User not found with email: " + scheduleDTO.getUserId());
            }
        } else if (!userRepository.existsById(scheduleDTO.getUserId())) {
            // If userId doesn't look like email, verify it exists as a user ID
            throw new ResourceNotFoundException("User not found with ID: " + scheduleDTO.getUserId());
        }

        // Convert DTO to entity
        AvailabilitySchedule schedule = convertToEntity(scheduleDTO);

        // Save and return
        return availabilityRepository.save(schedule);
    }

    @Override
    public AvailabilityDTO getScheduleForUser(String userId) {
        // First try to find directly by userId
        Optional<AvailabilitySchedule> scheduleByUserId = availabilityRepository.findByUserId(userId);

        // If not found and userId looks like an email, try finding the user and then their schedule
        if (scheduleByUserId.isEmpty() && userId.contains("@")) {
            Optional<User> userByEmail = userRepository.findByEmail(userId);
            if (userByEmail.isPresent()) {
                scheduleByUserId = availabilityRepository.findByUserId(userByEmail.get().getId());
                logger.info("Found user by email: {}, looking up schedule by ID: {}",
                        userId, userByEmail.get().getId());
            }
        }

        AvailabilitySchedule schedule = scheduleByUserId.orElseGet(() -> {
            // Create a default schedule if none exists
            logger.info("Creating new availability schedule for user: {}", userId);
            AvailabilitySchedule newSchedule = new AvailabilitySchedule(userId);
            return availabilityRepository.save(newSchedule);
        });

        return convertToDTO(schedule);
    }

    @Override
    public boolean isUserAvailableAt(String userId, DayOfWeek day, LocalTime time) {
        AvailabilitySchedule schedule = availabilityRepository.findByUserId(userId)
                .orElse(new AvailabilitySchedule(userId));

        return schedule.isAvailable(day, time);
    }

    @Override
    public boolean isUserAvailableAt(String userId, LocalDate date, LocalTime time) {
        AvailabilitySchedule schedule = availabilityRepository.findByUserId(userId)
                .orElse(new AvailabilitySchedule(userId));

        return schedule.isAvailable(date, time);
    }

    @Override
    public boolean isUserAvailableAt(String userId, LocalDateTime dateTime) {
        // Use the more specific date-based check first, falling back to day-of-week check
        return isUserAvailableAt(userId, dateTime.toLocalDate(), dateTime.toLocalTime());
    }

    @Override
    public AvailabilitySchedule setDayAvailability(String userId, DayOfWeek day, boolean isWorking,
                                                   LocalTime startTime, LocalTime endTime) {
        AvailabilitySchedule schedule = availabilityRepository.findByUserId(userId)
                .orElseGet(() -> new AvailabilitySchedule(userId));

        // Validate time range if working
        if (isWorking) {
            if (startTime == null || endTime == null) {
                throw new IllegalArgumentException("Start and end times must be provided when marking a day as working");
            }
            if (!startTime.isBefore(endTime)) {
                throw new IllegalArgumentException("Start time must be before end time");
            }
        }

        // Set the schedule for the day
        AvailabilitySchedule.DaySchedule daySchedule = new AvailabilitySchedule.DaySchedule(isWorking, startTime, endTime);
        schedule.getWeeklySchedule().put(day, daySchedule);

        // Save and return
        return availabilityRepository.save(schedule);
    }

    @Override
    public AvailabilitySchedule setDateAvailability(String userId, LocalDate date, boolean isWorking,
                                                    LocalTime startTime, LocalTime endTime) {
        AvailabilitySchedule schedule = availabilityRepository.findByUserId(userId)
                .orElseGet(() -> new AvailabilitySchedule(userId));

        // Validate time range if working
        if (isWorking) {
            if (startTime == null || endTime == null) {
                throw new IllegalArgumentException("Start and end times must be provided when marking a date as working");
            }
            if (!startTime.isBefore(endTime)) {
                throw new IllegalArgumentException("Start time must be before end time");
            }
        }

        // Set the schedule for the specific date
        AvailabilitySchedule.DaySchedule daySchedule = new AvailabilitySchedule.DaySchedule(isWorking, startTime, endTime);
        schedule.getMonthlySchedule().put(date, daySchedule);

        // Save and return
        return availabilityRepository.save(schedule);
    }

    @Override
    public AvailabilitySchedule setDateRangeAvailability(
            String userId,
            LocalDate startDate,
            LocalDate endDate,
            boolean isWorking,
            LocalTime startTime,
            LocalTime endTime) {

        // التحقق من وجود المستخدم
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // التحقق من صحة الوقت إذا كان يوم عمل
        if (isWorking) {
            if (startTime == null || endTime == null) {
                throw new IllegalArgumentException("Start and end times are required for working days");
            }
            if (!startTime.isBefore(endTime)) {
                throw new IllegalArgumentException("Start time must be before end time");
            }
        }

        // جلب الجدول الحالي أو إنشاء جديد
        AvailabilitySchedule schedule = availabilityRepository.findByUserId(userId)
                .orElse(new AvailabilitySchedule(userId));

        // تطبيق التغييرات على النطاق الزمني
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            AvailabilitySchedule.DaySchedule daySchedule = new AvailabilitySchedule.DaySchedule(isWorking, startTime, endTime);
            schedule.getMonthlySchedule().put(currentDate, daySchedule);
            currentDate = currentDate.plusDays(1);
        }

        // حفظ التحديثات
        return availabilityRepository.save(schedule);
    }

    @Override
    public List<String> findAvailableDeliveryPersonsAt(LocalDateTime dateTime) {
        List<User> deliveryPersons = userRepository.findAllByRolesContaining(Role.ROLE_DELIVERY_PERSON);
        List<String> availableUserIds = new ArrayList<>();

        for (User user : deliveryPersons) {
            if (user.isEnabled() && user.isAvailable() && isUserAvailableAt(user.getId(), dateTime)) {
                availableUserIds.add(user.getId());
            }
        }

        return availableUserIds;
    }

    /**
     * Generate default monthly schedule based on weekly schedule for a specific month
     * This is useful for filling in a full month with the weekly pattern
     */
    public AvailabilitySchedule generateMonthlyScheduleFromWeeklyPattern(
            String userId, LocalDate startDate, LocalDate endDate) {

        AvailabilitySchedule schedule = availabilityRepository.findByUserId(userId)
                .orElseGet(() -> new AvailabilitySchedule(userId));

        // Ensure we have a weekly schedule to base the monthly schedule on
        if (schedule.getWeeklySchedule() == null || schedule.getWeeklySchedule().isEmpty()) {
            throw new IllegalStateException("No weekly schedule available to generate monthly schedule");
        }

        // For each date in the range
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            // Only add if there isn't already a specific override for this date
            if (!schedule.getMonthlySchedule().containsKey(currentDate)) {
                // Get the day of week schedule
                DayOfWeek dayOfWeek = currentDate.getDayOfWeek();
                AvailabilitySchedule.DaySchedule daySchedule = schedule.getWeeklySchedule().get(dayOfWeek);

                if (daySchedule != null) {
                    // Copy the day schedule to the date-specific schedule
                    schedule.getMonthlySchedule().put(currentDate, new AvailabilitySchedule.DaySchedule(
                            daySchedule.isWorking(),
                            daySchedule.getStartTime(),
                            daySchedule.getEndTime()
                    ));
                }
            }

            // Move to next day
            currentDate = currentDate.plusDays(1);
        }

        // Save and return
        return availabilityRepository.save(schedule);
    }

    /**
     * Bulk set availability for specific weekdays within a date range
     * For example, set all Mondays and Tuesdays within March
     */
    public AvailabilitySchedule setWeekdaysInRangeAvailability(
            String userId, LocalDate startDate, LocalDate endDate,
            Set<DayOfWeek> daysOfWeek, boolean isWorking,
            LocalTime startTime, LocalTime endTime) {

        AvailabilitySchedule schedule = availabilityRepository.findByUserId(userId)
                .orElseGet(() -> new AvailabilitySchedule(userId));

        // Validate time range if working
        if (isWorking) {
            if (startTime == null || endTime == null) {
                throw new IllegalArgumentException("Start and end times must be provided when marking dates as working");
            }
            if (!startTime.isBefore(endTime)) {
                throw new IllegalArgumentException("Start time must be before end time");
            }
        }

        // Create the schedule entry
        AvailabilitySchedule.DaySchedule daySchedule = new AvailabilitySchedule.DaySchedule(isWorking, startTime, endTime);

        // Apply to all matching dates in the range (inclusive)
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            if (daysOfWeek.contains(currentDate.getDayOfWeek())) {
                schedule.getMonthlySchedule().put(currentDate, daySchedule);
            }
            currentDate = currentDate.plusDays(1);
        }

        // Save and return
        return availabilityRepository.save(schedule);
    }

    /**
     * Copy availability from one month to another
     * Useful for quickly replicating schedules
     */
    public AvailabilitySchedule copyMonthlyAvailability(
            String userId, LocalDate sourceMonthStart, LocalDate targetMonthStart) {

        AvailabilitySchedule schedule = availabilityRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Availability schedule not found for user: " + userId));

        // Determine the last days of both months
        LocalDate sourceMonthEnd = sourceMonthStart.withDayOfMonth(sourceMonthStart.lengthOfMonth());
        LocalDate targetMonthEnd = targetMonthStart.withDayOfMonth(targetMonthStart.lengthOfMonth());

        // Calculate the day offset between the months (first day to first day)
        long daysBetweenMonths = targetMonthStart.toEpochDay() - sourceMonthStart.toEpochDay();

        // Copy schedule entries, adjusting the date
        Map<LocalDate, AvailabilitySchedule.DaySchedule> newEntries = new HashMap<>();

        schedule.getMonthlySchedule().forEach((date, daySchedule) -> {
            if (!date.isBefore(sourceMonthStart) && !date.isAfter(sourceMonthEnd)) {
                // Calculate the corresponding date in the target month
                int dayOfMonth = date.getDayOfMonth();

                // Handle cases where the target month has fewer days
                if (dayOfMonth <= targetMonthEnd.getDayOfMonth()) {
                    LocalDate targetDate = date.plusDays(daysBetweenMonths);

                    // Only add if it's in the right month
                    if (targetDate.getMonth() == targetMonthStart.getMonth() &&
                            targetDate.getYear() == targetMonthStart.getYear()) {

                        newEntries.put(targetDate, new AvailabilitySchedule.DaySchedule(
                                daySchedule.isWorking(),
                                daySchedule.getStartTime(),
                                daySchedule.getEndTime()
                        ));
                    }
                }
            }
        });

        // Add the new entries to the schedule
        schedule.getMonthlySchedule().putAll(newEntries);

        // Save and return
        return availabilityRepository.save(schedule);
    }

    /**
     * Clear all monthly schedule entries
     */
    public AvailabilitySchedule clearAllMonthlySchedule(String userId) {
        AvailabilitySchedule schedule = availabilityRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Availability schedule not found for user: " + userId));

        // Clear the monthly schedule
        schedule.getMonthlySchedule().clear();

        // Save and return
        return availabilityRepository.save(schedule);
    }

    // Helper methods to convert between DTO and entity
    private AvailabilityDTO convertToDTO(AvailabilitySchedule schedule) {
        AvailabilityDTO dto = new AvailabilityDTO();
        dto.setId(schedule.getId());
        dto.setUserId(schedule.getUserId());

        // Convert weekly schedule
        Map<DayOfWeek, AvailabilityDTO.DayScheduleDTO> weeklyScheduleDTO = new HashMap<>();
        for (Map.Entry<DayOfWeek, AvailabilitySchedule.DaySchedule> entry : schedule.getWeeklySchedule().entrySet()) {
            AvailabilitySchedule.DaySchedule daySchedule = entry.getValue();
            AvailabilityDTO.DayScheduleDTO dayScheduleDTO = new AvailabilityDTO.DayScheduleDTO(
                    daySchedule.isWorking(),
                    daySchedule.getStartTime(),
                    daySchedule.getEndTime()
            );
            weeklyScheduleDTO.put(entry.getKey(), dayScheduleDTO);
        }
        dto.setWeeklySchedule(weeklyScheduleDTO);

        // Convert monthly schedule
        Map<LocalDate, AvailabilityDTO.DayScheduleDTO> monthlyScheduleDTO = new HashMap<>();
        if (schedule.getMonthlySchedule() != null) {
            for (Map.Entry<LocalDate, AvailabilitySchedule.DaySchedule> entry : schedule.getMonthlySchedule().entrySet()) {
                AvailabilitySchedule.DaySchedule daySchedule = entry.getValue();
                AvailabilityDTO.DayScheduleDTO dayScheduleDTO = new AvailabilityDTO.DayScheduleDTO(
                        daySchedule.isWorking(),
                        daySchedule.getStartTime(),
                        daySchedule.getEndTime()
                );
                monthlyScheduleDTO.put(entry.getKey(), dayScheduleDTO);
            }
        }
        dto.setMonthlySchedule(monthlyScheduleDTO);

        return dto;
    }

    private AvailabilitySchedule convertToEntity(AvailabilityDTO dto) {
        AvailabilitySchedule schedule = new AvailabilitySchedule();
        schedule.setId(dto.getId());
        schedule.setUserId(dto.getUserId());

        // Convert weekly schedule
        Map<DayOfWeek, AvailabilitySchedule.DaySchedule> weeklySchedule = new HashMap<>();
        if (dto.getWeeklySchedule() != null) {
            for (Map.Entry<DayOfWeek, AvailabilityDTO.DayScheduleDTO> entry : dto.getWeeklySchedule().entrySet()) {
                AvailabilityDTO.DayScheduleDTO dayScheduleDTO = entry.getValue();
                AvailabilitySchedule.DaySchedule daySchedule = new AvailabilitySchedule.DaySchedule(
                        dayScheduleDTO.isWorking(),
                        dayScheduleDTO.getStartTime(),
                        dayScheduleDTO.getEndTime()
                );
                weeklySchedule.put(entry.getKey(), daySchedule);
            }
        }
        schedule.setWeeklySchedule(weeklySchedule);

        // Convert monthly schedule
        Map<LocalDate, AvailabilitySchedule.DaySchedule> monthlySchedule = new TreeMap<>();
        if (dto.getMonthlySchedule() != null) {
            for (Map.Entry<LocalDate, AvailabilityDTO.DayScheduleDTO> entry : dto.getMonthlySchedule().entrySet()) {
                AvailabilityDTO.DayScheduleDTO dayScheduleDTO = entry.getValue();
                AvailabilitySchedule.DaySchedule daySchedule = new AvailabilitySchedule.DaySchedule(
                        dayScheduleDTO.isWorking(),
                        dayScheduleDTO.getStartTime(),
                        dayScheduleDTO.getEndTime()
                );
                monthlySchedule.put(entry.getKey(), daySchedule);
            }
        }
        schedule.setMonthlySchedule(monthlySchedule);

        return schedule;
    }


    /**
     * Check if a delivery person already has a meaningful schedule
     */
    public boolean hasExistingSchedule(String userId) {
        try {
            AvailabilitySchedule schedule = availabilityRepository.findByUserId(userId)
                    .orElse(null);

            if (schedule == null) {
                return false;
            }

            // Check if any weekly schedule days are set to working
            boolean hasWeeklySchedule = schedule.getWeeklySchedule().values().stream()
                    .anyMatch(day -> day.isWorking());

            // Check if any monthly schedule dates are set
            boolean hasMonthlySchedule = schedule.getMonthlySchedule() != null &&
                    !schedule.getMonthlySchedule().isEmpty();

            return hasWeeklySchedule || hasMonthlySchedule;
        } catch (Exception e) {
            logger.error("Error checking existing schedule for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Get delivery persons without schedules
     */
    public List<User> getDeliveryPersonsWithoutSchedule() {
        List<User> allDeliveryPersons = userRepository.findAllByRolesContaining(Role.ROLE_DELIVERY_PERSON);
        return allDeliveryPersons.stream()
                .filter(user -> !hasExistingSchedule(user.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Create default schedule template for a delivery person
     */
    public AvailabilitySchedule createDefaultScheduleForDeliveryPerson(String userId) {
        // Verify user exists and is a delivery person
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        boolean isDeliveryPerson = user.getRoles().stream()
                .anyMatch(role -> role.name().equals("ROLE_DELIVERY_PERSON"));

        if (!isDeliveryPerson) {
            throw new IllegalArgumentException("User is not a delivery person");
        }

        // Check if schedule already exists
        if (hasExistingSchedule(userId)) {
            throw new IllegalStateException("User already has a schedule");
        }

        // Create default schedule (Monday to Friday, 9 AM to 5 PM)
        AvailabilitySchedule schedule = new AvailabilitySchedule(userId);

        // Set default working hours for weekdays
        LocalTime defaultStart = LocalTime.of(9, 0);  // 9:00 AM
        LocalTime defaultEnd = LocalTime.of(17, 0);   // 5:00 PM

        for (DayOfWeek day : DayOfWeek.values()) {
            boolean isWeekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            if (!isWeekend) {
                schedule.getWeeklySchedule().put(day,
                        new AvailabilitySchedule.DaySchedule(true, defaultStart, defaultEnd));
            } else {
                schedule.getWeeklySchedule().put(day,
                        new AvailabilitySchedule.DaySchedule(false, null, null));
            }
        }

        return availabilityRepository.save(schedule);
    }

    /**
     * Update existing schedule (merge with existing data)
     */
    public AvailabilitySchedule updateExistingSchedule(AvailabilityDTO scheduleDTO) {
        AvailabilitySchedule existingSchedule = availabilityRepository.findByUserId(scheduleDTO.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found for user: " + scheduleDTO.getUserId()));

        // Update weekly schedule
        if (scheduleDTO.getWeeklySchedule() != null) {
            for (Map.Entry<DayOfWeek, AvailabilityDTO.DayScheduleDTO> entry : scheduleDTO.getWeeklySchedule().entrySet()) {
                AvailabilityDTO.DayScheduleDTO dayDTO = entry.getValue();
                AvailabilitySchedule.DaySchedule daySchedule = new AvailabilitySchedule.DaySchedule(
                        dayDTO.isWorking(), dayDTO.getStartTime(), dayDTO.getEndTime());
                existingSchedule.getWeeklySchedule().put(entry.getKey(), daySchedule);
            }
        }

        // Update monthly schedule
        if (scheduleDTO.getMonthlySchedule() != null) {
            for (Map.Entry<LocalDate, AvailabilityDTO.DayScheduleDTO> entry : scheduleDTO.getMonthlySchedule().entrySet()) {
                AvailabilityDTO.DayScheduleDTO dayDTO = entry.getValue();
                AvailabilitySchedule.DaySchedule daySchedule = new AvailabilitySchedule.DaySchedule(
                        dayDTO.isWorking(), dayDTO.getStartTime(), dayDTO.getEndTime());
                existingSchedule.getMonthlySchedule().put(entry.getKey(), daySchedule);
            }
        }

        return availabilityRepository.save(existingSchedule);
    }
}