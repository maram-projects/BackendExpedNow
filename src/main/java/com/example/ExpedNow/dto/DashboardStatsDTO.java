package com.example.ExpedNow.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DashboardStatsDTO {
    private long totalUsers;
    private Map<String, Long> usersByRole;
    private List<UserDTO> recentRegistrations;
}