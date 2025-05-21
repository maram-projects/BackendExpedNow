package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.Bonus;
import com.example.ExpedNow.models.enums.BonusStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BonusRepository extends MongoRepository<Bonus, String> {
    List<Bonus> findByDeliveryPersonId(String deliveryPersonId);
    List<Bonus> findByStatus(BonusStatus status);
    List<Bonus> findByStartDateBetween(LocalDateTime start, LocalDateTime end);
}