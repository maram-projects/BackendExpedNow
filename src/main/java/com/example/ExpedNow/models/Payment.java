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

    private String deliveryId;

    private String clientId;   // العميل الذي قام بالدفع
    private Double convertedAmount;   // Amount in Stripe currency (USD)
    private Double exchangeRate;      // Exchange rate used
    private String convertedCurrency; // Currency sent to Stripe (USD)

    // Changed to Double (wrapper class) to allow null values
    private Double amount;     // المبلغ
    private Double finalAmountAfterDiscount; // المبلغ بعد الخصم
    private String currency;   // العملة (TND, USD, EUR, etc.)

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
    private Double discountAmount; // قيمة الخصم (changed to Double)

    // Additional fields needed by the service
    private String clientSecret; // For Stripe payments
    private String discountCode; // Discount code used
    private String deliveryPersonId; // رقم الموصل المسؤول عن التوصيل
    private Double deliveryPersonShare; // حصة الموصل من المبلغ (مثلا 70%)
    private Boolean deliveryPersonPaid = false; // هل تم دفع حصة الموصل

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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.method = paymentMethod;
    }
}