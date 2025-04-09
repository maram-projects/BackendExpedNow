package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.enums.Role;
import com.example.ExpedNow.models.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {


    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRolesInAndEnabled(List<Role> roles, boolean enabled);
}