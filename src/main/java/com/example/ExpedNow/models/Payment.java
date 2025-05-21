package com.example.ExpedNow.models;

import com.example.ExpedNow.models.enums.PaymentMethod;
import com.example.ExpedNow.models.enums.PaymentStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import java.time.LocalDateTime;

@Document(collection = "payments")
@Data
public class Payment {
    @Id
    private String id;

    private String deliveryId; // الرابط مع طلب التوصيل
    private String clientId;   // العميل الذي قام بالدفع

    private double amount;     // المبلغ
    private double finalAmountAfterDiscount; // المبلغ بعد الخصم

    @Field(targetType = FieldType.STRING)
    private PaymentMethod method; // طريقة الدفع

    @Field(targetType = FieldType.STRING)
    private PaymentStatus status; // حالة الدفع

    // معلومات إضافية حسب طريقة الدفع
    private String transactionId; // رقم المعاملة من البنك أو بوابة الدفع
    private LocalDateTime paymentDate; // تاريخ الدفع
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
}
