package com.example.ExpedNow.controllers;

import io.sentry.Sentry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/test")  // بدون /public
public class DirectSentryTestController {

    @GetMapping("/error")
    public ResponseEntity<String> testError() {
        try {
            // هذا راح يرمي error ويرسله لـ Sentry
            throw new RuntimeException("🚨 DIRECT TEST ERROR: " + LocalDateTime.now());
        } catch (Exception e) {
            Sentry.captureException(e);
            return ResponseEntity.status(500).body("Error sent to Sentry: " + e.getMessage());
        }
    }
}