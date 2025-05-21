package com.example.ExpedNow.models.enums;

public enum PaymentStatus {
    PENDING,     // في انتظار الدفع
    PAID,        // تم الدفع
    FAILED,      // فشل الدفع
    REFUNDED,    // تم الاسترجاع
    PARTIALLY_REFUNDED
}
