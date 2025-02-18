package com.example.ExpedNow.security.Keys;

import java.util.Base64;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class JwtSecretGenerator {
    public static void main(String[] args) throws Exception {
        // توليد مفتاح عشوائي
        KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");
        keyGen.init(256); // نختارو 256-bit key
        SecretKey secretKey = keyGen.generateKey();

        // تحويل المفتاح إلى Base64
        String encodedKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());
        System.out.println("Generated JWT Secret Key: " + encodedKey);
    }
}

