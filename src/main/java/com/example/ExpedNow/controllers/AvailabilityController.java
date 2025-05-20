package com.example.ExpedNow.controllers;

import com.example.ExpedNow.dto.AvailabilityDTO;
import com.example.ExpedNow.exception.ResourceNotFoundException;
import com.example.ExpedNow.models.AvailabilitySchedule;
import com.example.ExpedNow.models.User;
import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.repositories.AvailabilityRepository;
import com.example.ExpedNow.repositories.UserRepository;
import com.example.ExpedNow.services.core.AvailabilityServiceInterface;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
@CrossOrigin(origins = "http://localhost:4200") // أو البورت بتاعك

@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {
    private static final Logger logger = LoggerFactory.getLogger(AvailabilityController.class);

    private final AvailabilityServiceInterface availabilityService;
    private final UserRepository userRepository;
    private final AvailabilityRepository availabilityRepository;

    @Autowired
    public AvailabilityController(AvailabilityServiceInterface availabilityService,
                                  UserRepository userRepository,
                                  AvailabilityRepository availabilityRepository) {
        this.availabilityService = availabilityService;
        this.userRepository = userRepository;
        this.availabilityRepository = availabilityRepository;
    }

    // Basic CRUD operations

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or #scheduleDTO.userId == authentication.principal.id")
    public ResponseEntity<?> createOrUpdateSchedule(@RequestBody @Valid AvailabilityDTO scheduleDTO,
                                                    Authentication authentication) {
        try {
            // Log the operation
            String currentUser = authentication.getName();
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

            logger.info("User {} {} schedule for user {}",
                    currentUser,
                    isAdmin ? "creating/updating" : "updating own",
                    scheduleDTO.getUserId());

            AvailabilitySchedule savedSchedule = availabilityService.saveSchedule(scheduleDTO);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Schedule saved successfully",
                    "schedule", savedSchedule
            ));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error saving schedule for user {}: {}", scheduleDTO.getUserId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "An error occurred while saving the schedule"));
        }
    }


    @GetMapping("/{userId}")
    @PreAuthorize("@securityService.canAccessAvailability(authentication, #userId)")
    public ResponseEntity<?> getSchedule(@PathVariable String userId,
                                         Authentication authentication) {
        try {
            AvailabilityDTO schedule = availabilityService.getScheduleForUser(userId);

            // Check if this is the user's first time viewing their schedule
            boolean isNewSchedule = schedule.getWeeklySchedule().values().stream()
                    .allMatch(day -> !day.isWorking());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "schedule", schedule,
                    "isNewSchedule", isNewSchedule
            ));
        } catch (Exception e) {
            logger.error("Error retrieving schedule for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error retrieving schedule"));
        }
    }

    // Admin specific endpoint to create schedule for delivery person
    @PostMapping("/admin/create-for-delivery-person/{deliveryPersonId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminCreateScheduleForDeliveryPerson(
            @PathVariable String deliveryPersonId,
            @RequestBody @Valid AvailabilityDTO scheduleDTO,
            Authentication authentication) {

        try {
            // Verify the user is a delivery person
            User deliveryPerson = userRepository.findById(deliveryPersonId)
                    .orElseThrow(() -> new ResourceNotFoundException("Delivery person not found"));

            boolean isDeliveryPerson = deliveryPerson.getRoles().stream()
                    .anyMatch(role -> role.name().equals("ROLE_DELIVERY_PERSON"));

            if (!isDeliveryPerson) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "message", "User is not a delivery person"));
            }

            // Set the userId in the DTO
            scheduleDTO.setUserId(deliveryPersonId);

            // Check if delivery person already has a schedule
            try {
                AvailabilityDTO existingSchedule = availabilityService.getScheduleForUser(deliveryPersonId);
                boolean hasExistingSchedule = existingSchedule.getWeeklySchedule().values().stream()
                        .anyMatch(day -> day.isWorking()) ||
                        (existingSchedule.getMonthlySchedule() != null && !existingSchedule.getMonthlySchedule().isEmpty());

                if (hasExistingSchedule) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of(
                                    "success", false,
                                    "message", "Delivery person already has a schedule. Use update endpoint instead.",
                                    "existingSchedule", existingSchedule
                            ));
                }
            } catch (Exception e) {
                // No existing schedule found, proceed with creation
                logger.info("No existing schedule found for delivery person {}, creating new one", deliveryPersonId);
            }

            AvailabilitySchedule savedSchedule = availabilityService.saveSchedule(scheduleDTO);

            logger.info("Admin {} created schedule for delivery person {}",
                    authentication.getName(), deliveryPersonId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Schedule created successfully for delivery person",
                    "schedule", savedSchedule
            ));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating schedule for delivery person {}: {}", deliveryPersonId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error creating schedule"));
        }
    }

    // Get all delivery persons without schedules (Admin only)
    @GetMapping("/admin/delivery-persons-without-schedule")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getDeliveryPersonsWithoutSchedule() {
        try {
            List<User> allDeliveryPersons = userRepository.findAllByRolesContaining(Role.ROLE_DELIVERY_PERSON);
            List<Map<String, Object>> deliveryPersonsWithoutSchedule = new ArrayList<>();

            for (User person : allDeliveryPersons) {
                try {
                    AvailabilityDTO schedule = availabilityService.getScheduleForUser(person.getId());
                    boolean hasSchedule = schedule.getWeeklySchedule().values().stream()
                            .anyMatch(day -> day.isWorking()) ||
                            (schedule.getMonthlySchedule() != null && !schedule.getMonthlySchedule().isEmpty());

                    if (!hasSchedule) {
                        deliveryPersonsWithoutSchedule.add(Map.of(
                                "id", person.getId(),
                                "email", person.getEmail(),
                                "firstName", person.getFirstName() != null ? person.getFirstName() : "",
                                "lastName", person.getLastName() != null ? person.getLastName() : "",
                                "enabled", person.isEnabled(),
                                "available", person.isAvailable()
                        ));
                    }
                } catch (Exception e) {
                    // If there's an error getting the schedule, assume no schedule exists
                    deliveryPersonsWithoutSchedule.add(Map.of(
                            "id", person.getId(),
                            "email", person.getEmail(),
                            "firstName", person.getFirstName() != null ? person.getFirstName() : "",
                            "lastName", person.getLastName() != null ? person.getLastName() : "",
                            "enabled", person.isEnabled(),
                            "available", person.isAvailable()
                    ));
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "deliveryPersonsWithoutSchedule", deliveryPersonsWithoutSchedule,
                    "total", deliveryPersonsWithoutSchedule.size()
            ));

        } catch (Exception e) {
            logger.error("Error getting delivery persons without schedule: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error retrieving delivery persons"));
        }
    }


    // Get schedule statistics (Admin only)
    @GetMapping("/admin/schedule-statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getScheduleStatistics() {
        try {
            List<User> allDeliveryPersons = userRepository.findAllByRolesContaining(Role.ROLE_DELIVERY_PERSON);
            int totalDeliveryPersons = allDeliveryPersons.size();
            int withSchedule = 0;
            int withoutSchedule = 0;

            for (User person : allDeliveryPersons) {
                try {
                    AvailabilityDTO schedule = availabilityService.getScheduleForUser(person.getId());
                    boolean hasSchedule = schedule.getWeeklySchedule().values().stream()
                            .anyMatch(day -> day.isWorking()) ||
                            (schedule.getMonthlySchedule() != null && !schedule.getMonthlySchedule().isEmpty());

                    if (hasSchedule) {
                        withSchedule++;
                    } else {
                        withoutSchedule++;
                    }
                } catch (Exception e) {
                    withoutSchedule++;
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "statistics", Map.of(
                            "totalDeliveryPersons", totalDeliveryPersons,
                            "withSchedule", withSchedule,
                            "withoutSchedule", withoutSchedule,
                            "scheduleCompletionRate", totalDeliveryPersons > 0 ?
                                    (double) withSchedule / totalDeliveryPersons * 100 : 0
                    )
            ));

        } catch (Exception e) {
            logger.error("Error getting schedule statistics: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error retrieving statistics"));
        }
    }

    // Validate schedule before saving
    @PostMapping("/validate")
    @PreAuthorize("hasRole('ADMIN') or #scheduleDTO.userId == authentication.principal.id")
    public ResponseEntity<?> validateSchedule(@RequestBody @Valid AvailabilityDTO scheduleDTO) {
        try {
            List<String> warnings = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            // Validate weekly schedule
            if (scheduleDTO.getWeeklySchedule() != null) {
                for (Map.Entry<DayOfWeek, AvailabilityDTO.DayScheduleDTO> entry : scheduleDTO.getWeeklySchedule().entrySet()) {
                    AvailabilityDTO.DayScheduleDTO day = entry.getValue();
                    if (day.isWorking()) {
                        if (day.getStartTime() == null || day.getEndTime() == null) {
                            errors.add("Working day " + entry.getKey() + " must have start and end times");
                        } else if (!day.getStartTime().isBefore(day.getEndTime())) {
                            errors.add("Start time must be before end time for " + entry.getKey());
                        }

                        // Warning for long shifts
                        if (day.getStartTime() != null && day.getEndTime() != null) {
                            long hours = day.getStartTime().until(day.getEndTime(), java.time.temporal.ChronoUnit.HOURS);
                            if (hours > 12) {
                                warnings.add("Long shift detected for " + entry.getKey() + " (" + hours + " hours)");
                            }
                        }
                    }
                }
            }

            // Check if no working days are set
            boolean hasWorkingDays = scheduleDTO.getWeeklySchedule() != null &&
                    scheduleDTO.getWeeklySchedule().values().stream().anyMatch(day -> day.isWorking());
            boolean hasWorkingDates = scheduleDTO.getMonthlySchedule() != null &&
                    scheduleDTO.getMonthlySchedule().values().stream().anyMatch(day -> day.isWorking());

            if (!hasWorkingDays && !hasWorkingDates) {
                warnings.add("No working days or dates specified in the schedule");
            }

            return ResponseEntity.ok(Map.of(
                    "valid", errors.isEmpty(),
                    "errors", errors,
                    "warnings", warnings
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error validating schedule"));
        }
    }


    // Weekly schedule operations

    @PutMapping("/{userId}/day/{day}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public ResponseEntity<AvailabilitySchedule> updateDayAvailability(
            @PathVariable String userId,
            @PathVariable DayOfWeek day,
            @RequestParam boolean isWorking,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            Authentication authentication) {

        AvailabilitySchedule updatedSchedule = availabilityService.setDayAvailability(
                userId, day, isWorking, startTime, endTime);
        return ResponseEntity.ok(updatedSchedule);
    }

    @GetMapping("/{userId}/check/day")
    public ResponseEntity<Boolean> checkDayAvailability(
            @PathVariable String userId,
            @RequestParam DayOfWeek day,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) {

        boolean isAvailable = availabilityService.isUserAvailableAt(userId, day, time);
        return ResponseEntity.ok(isAvailable);
    }

    // Date-specific operations

    @PutMapping("/{userId}/date/{date}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public ResponseEntity<AvailabilitySchedule> updateDateAvailability(
            @PathVariable String userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam boolean isWorking,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            Authentication authentication) {

        AvailabilitySchedule updatedSchedule = availabilityService.setDateAvailability(
                userId, date, isWorking, startTime, endTime);
        return ResponseEntity.ok(updatedSchedule);
    }

    @PutMapping("/{userId}/daterange")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public ResponseEntity<?> updateDateRangeAvailability(
            @PathVariable String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam boolean isWorking,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime) {

        try {
            // التحقق من صحة المدخلات
            if (startDate.isAfter(endDate)) {
                return ResponseEntity.badRequest().body("تاريخ البداية يجب أن يكون قبل تاريخ النهاية");
            }

            if (isWorking && (startTime == null || endTime == null)) {
                return ResponseEntity.badRequest().body("يجب تحديد وقت البداية والنهاية عندما يكون اليوم عمل");
            }

            AvailabilitySchedule updatedSchedule = availabilityService.setDateRangeAvailability(
                    userId, startDate, endDate, isWorking, startTime, endTime);

            return ResponseEntity.ok(updatedSchedule);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("حدث خطأ أثناء تحديث النطاق الزمني: " + e.getMessage());
        }
    }

    @GetMapping("/{userId}/check/date")
    public ResponseEntity<Boolean> checkDateAvailability(
            @PathVariable String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) {

        boolean isAvailable = availabilityService.isUserAvailableAt(userId, date, time);
        return ResponseEntity.ok(isAvailable);
    }

    @GetMapping("/{userId}/check/datetime")
    public ResponseEntity<Boolean> checkDateTimeAvailability(
            @PathVariable String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTime) {

        boolean isAvailable = availabilityService.isUserAvailableAt(userId, dateTime);
        return ResponseEntity.ok(isAvailable);
    }

    // Utility operations

    @PostMapping("/{userId}/generate-monthly")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public ResponseEntity<AvailabilitySchedule> generateMonthlyFromWeekly(
            @PathVariable String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {

        AvailabilitySchedule schedule = availabilityService.generateMonthlyScheduleFromWeeklyPattern(
                userId, startDate, endDate);
        return ResponseEntity.ok(schedule);
    }

    @PutMapping("/{userId}/weekdays-in-range")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public ResponseEntity<AvailabilitySchedule> setWeekdaysInRange(
            @PathVariable String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam Set<DayOfWeek> daysOfWeek,
            @RequestParam boolean isWorking,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            Authentication authentication) {

        AvailabilitySchedule schedule = availabilityService.setWeekdaysInRangeAvailability(
                userId, startDate, endDate, daysOfWeek, isWorking, startTime, endTime);
        return ResponseEntity.ok(schedule);
    }

    @PostMapping("/{userId}/copy-month")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public ResponseEntity<AvailabilitySchedule> copyMonthAvailability(
            @PathVariable String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sourceMonth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetMonth,
            Authentication authentication) {

        AvailabilitySchedule schedule = availabilityService.copyMonthlyAvailability(
                userId, sourceMonth, targetMonth);
        return ResponseEntity.ok(schedule);
    }

    @DeleteMapping("/{userId}/clear-monthly")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public ResponseEntity<AvailabilitySchedule> clearMonthlySchedule(
            @PathVariable String userId,
            Authentication authentication) {

        AvailabilitySchedule schedule = availabilityService.clearAllMonthlySchedule(userId);
        return ResponseEntity.ok(schedule);
    }

    // Admin-specific operations

    @PutMapping("/admin/{userId}/date/{date}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AvailabilitySchedule> adminUpdateDateAvailability(
            @PathVariable String userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam boolean isWorking,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime) {

        AvailabilitySchedule updatedSchedule = availabilityService.setDateAvailability(
                userId, date, isWorking, startTime, endTime);
        return ResponseEntity.ok(updatedSchedule);
    }

    @PutMapping("/admin/{userId}/daterange")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AvailabilitySchedule> adminUpdateDateRangeAvailability(
            @PathVariable String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam boolean isWorking,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime) {

        AvailabilitySchedule updatedSchedule = availabilityService.setDateRangeAvailability(
                userId, startDate, endDate, isWorking, startTime, endTime);
        return ResponseEntity.ok(updatedSchedule);
    }

    @DeleteMapping("/{userId}/date/{date}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public ResponseEntity<AvailabilitySchedule> clearDateAvailability(
            @PathVariable String userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {

        AvailabilitySchedule schedule = availabilityRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Availability schedule not found for user: " + userId));

        schedule.getMonthlySchedule().remove(date);
        AvailabilitySchedule updatedSchedule = availabilityRepository.save(schedule);
        return ResponseEntity.ok(updatedSchedule);
    }

    @DeleteMapping("/admin/{userId}/date/{date}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AvailabilitySchedule> adminClearDateAvailability(
            @PathVariable String userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        AvailabilitySchedule schedule = availabilityRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Availability schedule not found for user: " + userId));

        schedule.getMonthlySchedule().remove(date);
        AvailabilitySchedule updatedSchedule = availabilityRepository.save(schedule);
        return ResponseEntity.ok(updatedSchedule);
    }

    @GetMapping("/{userId}/month")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public ResponseEntity<Map<LocalDate, AvailabilityDTO.DayScheduleDTO>> getMonthlySchedule(
            @PathVariable String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate monthStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate monthEndParam,
            Authentication authentication) {

        // Create a final variable for monthEnd
        final LocalDate monthEnd = (monthEndParam == null)
                ? monthStart.withDayOfMonth(monthStart.lengthOfMonth())
                : monthEndParam;

        AvailabilityDTO schedule = availabilityService.getScheduleForUser(userId);
        Map<LocalDate, AvailabilityDTO.DayScheduleDTO> filteredSchedule = new TreeMap<>();

        if (schedule.getMonthlySchedule() != null) {
            schedule.getMonthlySchedule().forEach((date, daySchedule) -> {
                if (!date.isBefore(monthStart) && !date.isAfter(monthEnd)) {
                    filteredSchedule.put(date, daySchedule);
                }
            });
        }

        return ResponseEntity.ok(filteredSchedule);
    }

    @DeleteMapping("/{userId}/daterange")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public ResponseEntity<AvailabilitySchedule> clearDateRangeAvailability(
            @PathVariable String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {

        AvailabilitySchedule schedule = availabilityRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Availability schedule not found for user: " + userId));

        schedule.getMonthlySchedule().entrySet().removeIf(entry ->
                !entry.getKey().isBefore(startDate) && !entry.getKey().isAfter(endDate));

        AvailabilitySchedule updatedSchedule = availabilityRepository.save(schedule);
        return ResponseEntity.ok(updatedSchedule);
    }

    @DeleteMapping("/admin/{userId}/daterange")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AvailabilitySchedule> adminClearDateRangeAvailability(
            @PathVariable String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        AvailabilitySchedule schedule = availabilityRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Availability schedule not found for user: " + userId));

        schedule.getMonthlySchedule().entrySet().removeIf(entry ->
                !entry.getKey().isBefore(startDate) && !entry.getKey().isAfter(endDate));

        AvailabilitySchedule updatedSchedule = availabilityRepository.save(schedule);
        return ResponseEntity.ok(updatedSchedule);
    }

    // Delivery person availability

    @GetMapping("/available-delivery-persons/date")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<String>> findAvailableDeliveryPersonsOnDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) {

        LocalDateTime dateTime = LocalDateTime.of(date, time);
        List<String> availablePersons = availabilityService.findAvailableDeliveryPersonsAt(dateTime);
        return ResponseEntity.ok(availablePersons);
    }

    @GetMapping("/available-delivery-persons/datetime")
    @PreAuthorize("hasRole('ADMIN') ")
    public ResponseEntity<List<String>> findAvailableDeliveryPersonsOnDateTime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTime) {

        List<String> availablePersons = availabilityService.findAvailableDeliveryPersonsAt(dateTime);
        return ResponseEntity.ok(availablePersons);
    }
}