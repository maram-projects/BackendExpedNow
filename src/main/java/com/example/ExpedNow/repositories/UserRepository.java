package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.models.User;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRolesInAndEnabled(List<Role> roles, boolean enabled);
    List<User> findAllByRolesContaining(Role role);

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


    Optional<User> findByAssignedVehicleId(String vehicleId);

    List<User> findByRolesContainingAndAssignedVehicleIdIsNull(String role);

}