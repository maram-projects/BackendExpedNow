package com.example.ExpedNow.models;

import com.example.ExpedNow.models.enums.PaymentMethod;
import com.example.ExpedNow.models.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "payments")
@Data
public class Payment {
    @Id
    private String id;

    private String deliveryId; // الرابط مع طلب التوصيل
    private String clientId;   // العميل الذي قام بالدفع

    private double amount;     // المبلغ
    private double finalAmountAfterDiscount; // المبلغ بعد الخصم
    private String currency;   // العملة (TND, USD, EUR, etc.) - الإضافة الجديدة

    @Field(targetType = FieldType.STRING)
    private PaymentMethod method; // طريقة الدفع

    @Field(targetType = FieldType.STRING)
    private PaymentStatus status; // حالة الدفع

    // معلومات إضافية حسب طريقة الدفع
    private String transactionId; // رقم المعاملة من البنك أو بوابة الدفع
    private String receiptUrl; // رابط الإيصال

    // معلومات خاصة بالبطاقة (إذا كانت طريقة الدفع بالبطاقة)
    private String cardLast4; // آخر 4 أرقام البطاقة
    private String cardBrand; // نوع البطاقة (Visa, MasterCard)

    // معلومات خاصة بالتحويل البنكي
    private String bankName;      // اسم البنك
    private String bankReference; // رقم المرجع البنكي

    // خصومات إذا وجدت
    private String discountId;   // رابط مع نموذج الخصم إذا استخدم العميل خصم
    private double discountAmount; // قيمة الخصم

    // Additional fields needed by the service
    private String clientSecret; // For Stripe payments
    private String discountCode; // Discount code used


    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime paymentDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime updatedAt;

    // Helper method to get payment method (since your field is named 'method')
    public PaymentMethod getPaymentMethod() {
        return this.method;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.method = paymentMethod;
    }
}