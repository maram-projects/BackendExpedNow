package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.UserLocation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserLocationRepository extends MongoRepository<UserLocation, String> {
    Optional<UserLocation> findByUserId(String userId);
}