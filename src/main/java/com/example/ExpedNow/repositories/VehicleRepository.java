package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.Vehicle;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VehicleRepository extends MongoRepository<Vehicle, String> {
}
