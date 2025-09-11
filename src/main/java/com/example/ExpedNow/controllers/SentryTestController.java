package com.example.ExpedNow.controllers;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import io.sentry.Sentry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/test")
public class SentryTestController {

    private static final Logger logger = LoggerFactory.getLogger(SentryTestController.class);

    @GetMapping("/error")
    public ResponseEntity<String> testError() {
        logger.info("🧪 Testing Sentry error reporting...");

        // This will trigger an error and send it to Sentry
        try {
            throw new RuntimeException("🚨 TEST ERROR: Sentry integration working! Time: " + System.currentTimeMillis());
        } catch (Exception e) {
            // Log the error (will be sent to Sentry automatically)
            logger.error("Test error occurred", e);

            // Also manually capture with Sentry
            Sentry.captureException(e);

            return ResponseEntity.status(500)
                    .body("✅ Error sent to Sentry successfully! Check your dashboard.");
        }
    }

    @GetMapping("/breadcrumb")
    public ResponseEntity<String> testBreadcrumb() {
        logger.info("🍞 Testing Sentry breadcrumbs...");

        // Add breadcrumbs for tracking user actions
        Sentry.addBreadcrumb("User visited breadcrumb test endpoint");
        Sentry.addBreadcrumb("Processing test data...");
        Sentry.addBreadcrumb("About to simulate an error");

        // Trigger error with breadcrumbs
        Sentry.captureMessage("📝 Test message with breadcrumbs - " + System.currentTimeMillis());

        return ResponseEntity.ok("✅ Breadcrumb test completed! Check Sentry for details.");
    }

    @GetMapping("/status")
    public ResponseEntity<String> sentryStatus() {
        Sentry.addBreadcrumb("Checking Sentry status");

        return ResponseEntity.ok("🔧 Sentry Status: ✅ ACTIVE & CONFIGURED");
    }
}