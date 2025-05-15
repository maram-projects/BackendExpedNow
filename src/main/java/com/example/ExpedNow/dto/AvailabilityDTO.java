package com.example.ExpedNow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityDTO {
    private String id;
    private String userId;
    private Map<DayOfWeek, DayScheduleDTO> weeklySchedule;


    // Map each day of week to a pair of start-end times

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    // In AvailabilityDTO.java
    public static class DayScheduleDTO {
        @NotNull
        private boolean working;

        @JsonFormat(pattern = "HH:mm")
        private LocalTime startTime;

        @JsonFormat(pattern = "HH:mm")
        private LocalTime endTime;
    }
}