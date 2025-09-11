package com.example.ExpedNow.controllers;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import io.sentry.Sentry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/public")
public class PublicSentryTestController {

    private static final Logger logger = LoggerFactory.getLogger(PublicSentryTestController.class);

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "✅ PUBLIC ENDPOINT WORKING");
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        response.put("path", request.getRequestURI());
        response.put("sentry_status", "Ready");

        logger.info("🔧 Public health endpoint accessed: {}", request.getRequestURI());
        Sentry.addBreadcrumb("Public health check called from: " + request.getRemoteAddr());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/sentry-test")
    public ResponseEntity<Map<String, Object>> sentryTest(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            logger.info("🧪 Testing Sentry error reporting...");

            // إرسال test message أولاً
            Sentry.captureMessage("🔔 SENTRY TEST MESSAGE: " + LocalDateTime.now());

            // ثم إرسال exception
            throw new RuntimeException("🚨 SENTRY TEST ERROR: " + LocalDateTime.now() + " - Path: " + request.getRequestURI());

        } catch (Exception e) {
            logger.error("Test error occurred for Sentry", e);
            Sentry.captureException(e);

            response.put("status", "✅ ERROR SENT TO SENTRY");
            response.put("message", "Check your Sentry dashboard");
            response.put("error_message", e.getMessage());
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/sentry-message")
    public ResponseEntity<Map<String, Object>> sentryMessage(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        logger.info("📝 Testing Sentry message...");

        String message = "📨 SENTRY MESSAGE TEST: " + LocalDateTime.now() + " - From: " + request.getRemoteAddr();
        Sentry.captureMessage(message);

        response.put("status", "✅ MESSAGE SENT TO SENTRY");
        response.put("message", "Check your Sentry dashboard");
        response.put("sent_message", message);
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/sentry-error")
    public ResponseEntity<Map<String, Object>> sentryError(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            logger.info("🧪 Testing Sentry error reporting (public)...");

            // This will trigger an error and send it to Sentry
            throw new RuntimeException("🚨 PUBLIC TEST ERROR: Sentry working! Time: " + LocalDateTime.now());
        } catch (Exception e) {
            logger.error("Public test error occurred", e);
            Sentry.captureException(e);

            response.put("status", "✅ ERROR SENT TO SENTRY SUCCESSFULLY");
            response.put("message", "Check your Sentry dashboard");
            response.put("error", e.getMessage());
            response.put("path", request.getRequestURI());
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/debug-security")
    public ResponseEntity<Map<String, Object>> debugSecurity(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        response.put("endpoint_status", "✅ PUBLIC - NO AUTH REQUIRED");
        response.put("path", request.getRequestURI());
        response.put("method", request.getMethod());
        response.put("remote_addr", request.getRemoteAddr());
        response.put("user_agent", request.getHeader("User-Agent"));
        response.put("authorization_header", request.getHeader("Authorization") != null ? "Present" : "Not Present");
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        logger.info("🔍 Security debug info: Path={}, Method={}, Auth={}",
                request.getRequestURI(),
                request.getMethod(),
                request.getHeader("Authorization") != null ? "Present" : "None"
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/sentry-post-test")
    public ResponseEntity<Map<String, Object>> sentryPostTest(@RequestBody(required = false) Map<String, Object> payload,
                                                              HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        logger.info("📮 POST Test - Sentry integration check");

        Sentry.captureMessage("📮 POST TEST: " + LocalDateTime.now() + " - Payload: " + payload);

        response.put("status", "✅ POST ENDPOINT WORKING");
        response.put("received_payload", payload);
        response.put("sentry_message_sent", true);
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        return ResponseEntity.ok(response);
    }
}