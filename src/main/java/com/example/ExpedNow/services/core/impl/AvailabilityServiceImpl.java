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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

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
        // Validate schedule
        if (scheduleDTO.getWeeklySchedule() == null || scheduleDTO.getWeeklySchedule().isEmpty()) {
            throw new IllegalArgumentException("Weekly schedule cannot be empty");
        }

        // Check for valid time ranges
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
    public boolean isUserAvailableAt(String userId, LocalDateTime dateTime) {
        return isUserAvailableAt(userId, dateTime.getDayOfWeek(), dateTime.toLocalTime());
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

    // Helper methods to convert between DTO and entity
    private AvailabilityDTO convertToDTO(AvailabilitySchedule schedule) {
        AvailabilityDTO dto = new AvailabilityDTO();
        dto.setId(schedule.getId());
        dto.setUserId(schedule.getUserId());

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
        return dto;
    }

    private AvailabilitySchedule convertToEntity(AvailabilityDTO dto) {
        AvailabilitySchedule schedule = new AvailabilitySchedule();
        schedule.setId(dto.getId());
        schedule.setUserId(dto.getUserId());

        Map<DayOfWeek, AvailabilitySchedule.DaySchedule> weeklySchedule = new HashMap<>();

        for (Map.Entry<DayOfWeek, AvailabilityDTO.DayScheduleDTO> entry : dto.getWeeklySchedule().entrySet()) {
            AvailabilityDTO.DayScheduleDTO dayScheduleDTO = entry.getValue();
            AvailabilitySchedule.DaySchedule daySchedule = new AvailabilitySchedule.DaySchedule(
                    dayScheduleDTO.isWorking(),
                    dayScheduleDTO.getStartTime(),
                    dayScheduleDTO.getEndTime()
            );
            weeklySchedule.put(entry.getKey(), daySchedule);
        }

        schedule.setWeeklySchedule(weeklySchedule);
        return schedule;
    }
}