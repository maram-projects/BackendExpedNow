package com.example.ExpedNow.models;

import com.example.ExpedNow.models.enums.PackageType;
import com.example.ExpedNow.models.enums.PaymentMethod;
import com.example.ExpedNow.models.enums.PaymentStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@Document(collection = "deliveriesRequest")

public class DeliveryRequest {
    @Id
    private String id;

    private String pickupAddress;

    private String deliveryAddress;

    private String packageDescription;

    private double packageWeight;

    private String vehicleId;


    // حقول جديدة للدفع
    private String paymentId;          // رابط مع عملية الدفع
    private PaymentStatus paymentStatus; // حالة الدفع

    // حقول الخصم
    private String discountCode;       // كود الخصم إذا استخدم
    private Double discountAmount;     // قيمة الخصم

    // طريقة الدفع المفضلة (اختياري)
    private PaymentMethod preferredPaymentMethod;
    private String clientId;

    private PackageType packageType;


    private String additionalInstructions;

    private DeliveryReqStatus status = DeliveryReqStatus.PENDING;



    private String deliveryPersonId;

    private Double amount;
    private Double finalAmountAfterDiscount;
    private String discountId;

    private LocalDateTime scheduledDate;
    private LocalDateTime createdAt;
    private LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;



    private String notes;

    // For location tracking
    private double pickupLatitude;

    private double pickupLongitude;

    private double deliveryLatitude;
    @LastModifiedDate
    private LocalDateTime updatedAt;
    private double deliveryLongitude;

    public enum DeliveryReqStatus {
        PENDING,
        ASSIGNED,
        APPROVED,
        IN_TRANSIT,
        DELIVERED,
        CANCELLED,
        EXPIRED // أضف هذا
    }


}