package com.example.ExpedNow.controllers;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import io.sentry.Sentry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/public") // Public endpoints - no auth needed
public class PublicSentryTestController {

    private static final Logger logger = LoggerFactory.getLogger(PublicSentryTestController.class);

    @GetMapping("/sentry-error")
    public ResponseEntity<String> testError() {
        logger.info("🧪 Testing Sentry error reporting (public)...");

        try {
            // This will trigger an error and send it to Sentry
            throw new RuntimeException("🚨 PUBLIC TEST ERROR: Sentry working! Time: " + System.currentTimeMillis());
        } catch (Exception e) {
            logger.error("Public test error occurred", e);
            Sentry.captureException(e);

            return ResponseEntity.status(500)
                    .body("✅ Error sent to Sentry successfully! Check dashboard.");
        }
    }

    @GetMapping("/sentry-message")
    public ResponseEntity<String> testMessage() {
        logger.info("📝 Testing Sentry message (public)...");

        // Send message to Sentry
        Sentry.captureMessage("📨 PUBLIC TEST MESSAGE: Sentry configured correctly! " + System.currentTimeMillis());

        return ResponseEntity.ok("✅ Message sent to Sentry! Check dashboard.");
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        Sentry.addBreadcrumb("Public health check called");
        return ResponseEntity.ok("🔧 Public Health: ✅ ACTIVE - Sentry Ready!");
    }
}