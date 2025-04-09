package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.Vehicle;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface VehicleRepository extends MongoRepository<Vehicle, String> {

    List<Vehicle> findByAvailable(boolean available);

}
