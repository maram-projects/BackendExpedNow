package com.example.ExpedNow.models.enums;

public enum PaymentStatus {
    PENDING,                // في الانتظار
    PROCESSING,            // قيد المعالجة
    COMPLETED,             // مكتمل
    FAILED,                // فشل
    CANCELLED,             // ملغي
    REFUNDED,              // مسترد
    PARTIALLY_REFUNDED,    // مسترد جزئياً
    PENDING_DELIVERY,      // في انتظار التسليم (للدفع نقداً)
    PENDING_VERIFICATION   // في انتظار التحقق (للتحويل البنكي)
}