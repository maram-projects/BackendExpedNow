package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.models.User;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    long countByEnabledTrueAndApprovedTrue();
    long countByEnabledFalseOrApprovedFalse();
    long countByRolesContains(Role role);
    Optional<User> findByEmail(String email);
    Optional<User> findById(String id);
    boolean existsByEmail(String email);
    List<User> findByRolesInAndEnabled(List<Role> roles, boolean enabled);
    List<User> findAllByRolesContaining(Role role);

    // Add this method for the BonusService
    @Query("{ 'roles': ?0 }")
    List<User> findByRole(String role);

    @Query("SELECT u FROM User u WHERE " +
            "(LOWER(u.firstName) LIKE LOWER(:query) OR " +
            "(LOWER(u.lastName) LIKE LOWER(:query) OR " +
            "(LOWER(u.email) LIKE LOWER(:query) OR " +
            "(LOWER(u.companyName) LIKE LOWER(:query)) AND " +
            "(u.userType = 'individual' OR u.userType = 'enterprise')")
    List<User> findByClientAttributes(String query);
    // Add this method for the DiscountService
    @Aggregation(pipeline = {
            "{ $match: { 'roles': 'CLIENT', 'enabled': true } }",
            "{ $lookup: { " +
                    "from: 'delivery_requests', " +
                    "let: { userId: '$_id' }, " +
                    "pipeline: [" +
                    "{ $match: { " +
                    "$expr: { $eq: ['$clientId', '$userId'] }, " +
                    "requestedAt: { $gte: ?1, $lte: ?2 }, " +
                    "status: 'DELIVERED'" +
                    "} }" +
                    "], " +
                    "as: 'deliveries'" +
                    "} }",
            "{ $addFields: { deliveryCount: { $size: '$deliveries' } } }",
            "{ $match: { deliveryCount: { $gte: ?0 } } }",
            "{ $project: { deliveries: 0 } }"
    })
    List<User> findClientsWithMinDeliveries(int minDeliveries, LocalDateTime startDate, LocalDateTime endDate);

    // Fixed aggregation query with proper pipeline
    @Aggregation(pipeline = {
            "{ '$match': { '_id': ?0 } }",
            "{ '$lookup': { "
                    + "from: 'vehicles', "
                    + "localField: 'assignedVehicleId', "
                    + "foreignField: '_id', "
                    + "as: 'assignedVehicle' "
                    + "}}",
            "{ '$unwind': { path: '$assignedVehicle', preserveNullAndEmptyArrays: true }}"
    })
    User findUserWithVehicle(String userId);
    @Query(value = "{email: ?0}", fields = "{email: 1}")
    Optional<String> findEmailOnlyByEmail(String email);

    Optional<User> findByAssignedVehicleId(String vehicleId);

    List<User> findByRolesContainingAndAssignedVehicleIdIsNull(String role);
    List<User> findByEnabled(boolean enabled);

    // In UserRepository.java
    List<User> findByApprovedFalseAndEnabledFalse();

    Optional<User> findByResetToken(String token);
}