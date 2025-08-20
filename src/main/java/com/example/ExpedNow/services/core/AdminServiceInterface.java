package com.example.ExpedNow.services.core;

import com.example.ExpedNow.dto.DashboardStatsDTO;
import com.example.ExpedNow.dto.UserDTO;
import com.example.ExpedNow.models.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

/**
 * Interface for administrative operations
 */
public interface AdminServiceInterface {

    /**
     * Gets dashboard statistics for admin view
     *
     * @return Dashboard statistics
     */
    DashboardStatsDTO getDashboardStats();

    /**
     * Gets all users in the system
     *
     * @return List of all users
     */
    List<UserDTO> getAllUsers();

    /**
     * Updates a user's status
     *
     * @param userId The ID of the user to update
     * @param status The new status
     */
    void updateUserStatus(String userId, String status);

    /**
     * Updates a user's roles
     *
     * @param userId The ID of the user to update
     * @param roles  The new set of roles
     */
    void updateUserRoles(String userId, Set<Role> roles);

    Page<UserDTO> getUsersWithFilters(String search, String role, Boolean active, Pageable pageable);

}