package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.Mission;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MissionRepository extends MongoRepository<Mission, String> {

    // استعلام للبحث عن المهام حسب معرف الشخص الموصل
    @Query("{ 'deliveryPerson.$id': ObjectId(?0) }")
    List<Mission> findByDeliveryPersonId(String deliveryPersonId);

    // استعلام للبحث عن المهام حسب الحالة
    @Query("{ status: { $in: ?0 } }")
    List<Mission> findByStatusIn(List<String> statuses);

    // إضافة: استعلام للبحث عن مهمة حسب معرف طلب التوصيل
    @Query("{ 'deliveryRequest.$id': ObjectId(?0) }")
    Optional<Mission> findByDeliveryRequestId(String deliveryRequestId);

    // استعلام مجمع باستخدام Aggregation للحصول على بيانات المهام مع معلومات التوصيل المرتبطة
    @Aggregation(pipeline = {
            "{ $match: { 'deliveryPerson.$id': ObjectId(?0) } }",
            "{ $lookup: { from: 'deliveriesRequest', localField: 'deliveryRequest.$id', foreignField: '_id', as: 'deliveryRequest' } }",
            "{ $unwind: '$deliveryRequest' }"
    })
    // NEW: Find missions by delivery person and status
    List<Mission> findByDeliveryPersonIdAndStatusIn(String deliveryPersonId, List<String> statuses);

    // NEW: Check if delivery person has any active missions
    @Query("SELECT COUNT(m) > 0 FROM Mission m WHERE m.deliveryPerson.id = :deliveryPersonId AND m.status IN :statuses")
    boolean existsByDeliveryPersonIdAndStatusIn(@Param("deliveryPersonId") String deliveryPersonId,
                                                @Param("statuses") List<String> statuses);

    // NEW: Get count of active missions for delivery person
    @Query("SELECT COUNT(m) FROM Mission m WHERE m.deliveryPerson.id = :deliveryPersonId AND m.status IN :statuses")
    long countByDeliveryPersonIdAndStatusIn(@Param("deliveryPersonId") String deliveryPersonId,
                                            @Param("statuses") List<String> statuses);



    /**
     * Find active missions for a delivery person (PENDING or IN_PROGRESS)
     */
    @Query("SELECT m FROM Mission m WHERE m.deliveryPerson.id = :deliveryPersonId AND m.status IN ('PENDING', 'IN_PROGRESS')")
    List<Mission> findActiveMissionsByDeliveryPerson(@Param("deliveryPersonId") String deliveryPersonId);

    /**
     * Check if delivery person has any active missions
     */
    @Query("SELECT COUNT(m) > 0 FROM Mission m WHERE m.deliveryPerson.id = :deliveryPersonId AND m.status IN ('PENDING', 'IN_PROGRESS')")
    boolean hasActiveMissions(@Param("deliveryPersonId") String deliveryPersonId);

    /**
     * Find completed missions by delivery person
     */
    @Query("SELECT m FROM Mission m WHERE m.deliveryPerson.id = :deliveryPersonId AND m.status = 'COMPLETED' ORDER BY m.endTime DESC")
    List<Mission> findCompletedMissionsByDeliveryPerson(@Param("deliveryPersonId") String deliveryPersonId);

    /**
     * Count active missions for a delivery person
     */
    @Query("SELECT COUNT(m) FROM Mission m WHERE m.deliveryPerson.id = :deliveryPersonId AND m.status IN ('PENDING', 'IN_PROGRESS')")
    long countActiveMissionsByDeliveryPerson(@Param("deliveryPersonId") String deliveryPersonId);

    /**
     * Find mission by delivery request ID and delivery person ID (for validation)
     */
    @Query("SELECT m FROM Mission m WHERE m.deliveryRequest.id = :deliveryRequestId AND m.deliveryPerson.id = :deliveryPersonId")
    Optional<Mission> findByDeliveryRequestIdAndDeliveryPersonId(
            @Param("deliveryRequestId") String deliveryRequestId,
            @Param("deliveryPersonId") String deliveryPersonId
    );


    /**
     * Find only truly active missions (IN_PROGRESS and PENDING only)
     */
    @Query("{ 'deliveryPerson.$id': ObjectId(?0), 'status': { '$in': ['IN_PROGRESS', 'PENDING'] } }")
    List<Mission> findActiveMissionsByDeliveryPersonId(String deliveryPersonId);

    /**
     * Check if delivery person has any truly active missions
     */
    @Query(value = "{ 'deliveryPerson.$id': ObjectId(?0), 'status': { '$in': ['IN_PROGRESS', 'PENDING'] } }", count = true)
    long countActiveMissionsByDeliveryPersonId(String deliveryPersonId);

    /**
     * Find completed missions for a delivery person
     */
    @Query("{ 'deliveryPerson.$id': ObjectId(?0), 'status': 'COMPLETED' }")
    List<Mission> findCompletedMissionsByDeliveryPersonId(String deliveryPersonId);

}