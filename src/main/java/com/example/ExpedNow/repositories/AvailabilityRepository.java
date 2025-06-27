    package com.example.ExpedNow.repositories;

    import com.example.ExpedNow.models.AvailabilitySchedule;
    import org.springframework.data.mongodb.repository.MongoRepository;
    import org.springframework.stereotype.Repository;

    import java.util.List;
    import java.util.Optional;

    @Repository
    public interface AvailabilityRepository extends MongoRepository<AvailabilitySchedule, String> {
        List<AvailabilitySchedule> findAllByUserId(String userId); // Add this method

        Optional<AvailabilitySchedule> findByUserId(String userId);
    }