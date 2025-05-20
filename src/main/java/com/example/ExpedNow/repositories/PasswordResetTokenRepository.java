package com.example.ExpedNow.repositories;

import com.example.ExpedNow.models.PasswordResetToken;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {
    // البحث بالرمز
    PasswordResetToken findByToken(String token);

    // حذف جميع الرموز المرتبطة بمستخدم (عند إنشاء رمز جديد)
    void deleteByUserId(String userId);
}