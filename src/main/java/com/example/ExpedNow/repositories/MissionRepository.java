package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.Mission;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

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
    List<Mission> findDeliveryPersonMissionsWithDetails(String deliveryPersonId);
}