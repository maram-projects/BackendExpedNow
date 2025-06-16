package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.Discount;
import com.example.ExpedNow.models.enums.DiscountType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiscountRepository extends MongoRepository<Discount, String> {
    Optional<Discount> findByCode(String code);

    // Add this method
    Optional<Discount> findByCodeAndClientId(String code, String clientId);

    List<Discount> findByClientId(String clientId);

    List<Discount> findByClientIdAndUsed(String clientId, boolean used);


    List<Discount> findByClientIdAndValidUntilAfterAndUsed(String clientId, LocalDateTime validUntil, boolean used);


    List<Discount> findByType(DiscountType type);

    // For statistics
    long countByValidUntilAfterAndUsed(LocalDateTime date, Boolean used);
    long countByUsed(Boolean used);
    long countByValidUntilBeforeAndUsed(LocalDateTime date, Boolean used);
    List<Discount> findByValidUntilBeforeAndUsed(LocalDateTime date, boolean used);
}