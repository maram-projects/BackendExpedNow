package com.example.ExpedNow.config;

import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;

    // Enhanced session tracking
    private final ConcurrentMap<String, UserSession> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> userDeliverySubscriptions = new ConcurrentHashMap<>();

    // Connection monitoring
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public WebSocketEventListener(SimpMessagingTemplate messagingTemplate, SimpUserRegistry userRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.userRegistry = userRegistry;

        // Start periodic cleanup and health monitoring
        startPeriodicTasks();
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        Authentication authentication = (Authentication) headerAccessor.getUser();

        if (authentication != null) {
            String userId = authentication.getName();

            // Create enhanced user session - FIXED: Using constructor instead of builder
            UserSession userSession = new UserSession();
            userSession.setSessionId(sessionId);
            userSession.setUserId(userId);
            userSession.setConnectedAt(LocalDateTime.now());
            userSession.setLastActivity(LocalDateTime.now());
            userSession.setActive(true);

            userSessions.put(sessionId, userSession);

            log.info("✅ User {} connected with session {} at {}",
                    userId, sessionId, userSession.getConnectedAt());

            // Enhanced user status notification
            broadcastUserStatus(userId, "ONLINE", true);

            // Send welcome message with connection info
            Map<String, Object> connectionInfo = Map.of(
                    "type", "CONNECTION_ESTABLISHED",
                    "sessionId", sessionId,
                    "userId", userId,
                    "connectedAt", userSession.getConnectedAt(),
                    "message", "Connection established successfully"
            );

            messagingTemplate.convertAndSendToUser(userId, "/queue/connection", connectionInfo);

        } else {
            log.warn("⚠️ WebSocket connection without authentication for session: {}", sessionId);
            // Optionally disconnect unauthorized sessions
            disconnectSession(sessionId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        UserSession userSession = userSessions.remove(sessionId);

        if (userSession != null) {
            String userId = userSession.getUserId();

            log.info("🔌 User {} disconnected from session {} (connected for {} minutes)",
                    userId, sessionId,
                    java.time.Duration.between(userSession.getConnectedAt(), LocalDateTime.now()).toMinutes());

            // Clean up user subscriptions
            userDeliverySubscriptions.remove(sessionId);

            // Check if user has other active sessions
            boolean hasOtherSessions = userSessions.values().stream()
                    .anyMatch(session -> session.getUserId().equals(userId) && session.isActive());

            if (!hasOtherSessions) {
                // User is completely offline
                broadcastUserStatus(userId, "OFFLINE", false);
            }

            // Send disconnect notification
            Map<String, Object> disconnectionInfo = Map.of(
                    "type", "CONNECTION_CLOSED",
                    "sessionId", sessionId,
                    "userId", userId,
                    "disconnectedAt", LocalDateTime.now(),
                    "reason", event.getCloseStatus() != null ? event.getCloseStatus().toString() : "Unknown"
            );

            // Try to send to other sessions of the same user
            messagingTemplate.convertAndSendToUser(userId, "/queue/connection", disconnectionInfo);
        } else {
            log.warn("⚠️ Disconnect event for unknown session: {}", sessionId);
        }
    }

    @EventListener
    public void handleSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();

        UserSession userSession = userSessions.get(sessionId);
        if (userSession != null) {
            userSession.setLastActivity(LocalDateTime.now());

            // Track delivery-specific subscriptions
            if (destination != null && destination.startsWith("/topic/delivery.")) {
                String deliveryId = destination.replace("/topic/delivery.", "");
                userDeliverySubscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                        .add(deliveryId);

                log.debug("📡 User {} subscribed to delivery {} updates",
                        userSession.getUserId(), deliveryId);
            }
        }
    }

    @EventListener
    public void handleUnsubscribeEvent(SessionUnsubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String subscriptionId = headerAccessor.getSubscriptionId();

        UserSession userSession = userSessions.get(sessionId);
        if (userSession != null) {
            userSession.setLastActivity(LocalDateTime.now());
            log.debug("📡 User {} unsubscribed from subscription {}",
                    userSession.getUserId(), subscriptionId);
        }
    }

    // Enhanced user status broadcasting
    private void broadcastUserStatus(String userId, String status, boolean isOnline) {
        Map<String, Object> statusUpdate = Map.of(
                "userId", userId,
                "status", status,
                "timestamp", LocalDateTime.now(),
                "isOnline", isOnline
        );

        // Broadcast to general user status topic
        messagingTemplate.convertAndSend("/topic/user.status." + userId, statusUpdate);

        // Also broadcast to all delivery topics this user might be part of
        broadcastToUserDeliveries(userId, statusUpdate);
    }

    private void broadcastToUserDeliveries(String userId, Map<String, Object> statusUpdate) {
        // Find all deliveries this user is subscribed to
        userSessions.values().stream()
                .filter(session -> session.getUserId().equals(userId))
                .flatMap(session -> userDeliverySubscriptions.getOrDefault(session.getSessionId(), Set.of()).stream())
                .distinct()
                .forEach(deliveryId -> {
                    messagingTemplate.convertAndSend("/topic/delivery." + deliveryId + ".status", statusUpdate);
                });
    }

    // Periodic tasks for connection health and cleanup
    private void startPeriodicTasks() {
        // Clean up inactive sessions every 5 minutes
        scheduler.scheduleAtFixedRate(this::cleanupInactiveSessions, 5, 5, TimeUnit.MINUTES);

        // Send heartbeat every 30 seconds
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 30, 30, TimeUnit.SECONDS);

        // Connection health check every minute
        scheduler.scheduleAtFixedRate(this::checkConnectionHealth, 1, 1, TimeUnit.MINUTES);
    }

    private void cleanupInactiveSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10); // 10 minutes timeout

        userSessions.entrySet().removeIf(entry -> {
            UserSession session = entry.getValue();
            if (session.getLastActivity().isBefore(cutoff)) {
                log.info("🧹 Cleaning up inactive session {} for user {}",
                        entry.getKey(), session.getUserId());
                userDeliverySubscriptions.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private void sendHeartbeat() {
        Map<String, Object> heartbeat = Map.of(
                "type", "HEARTBEAT",
                "timestamp", LocalDateTime.now(),
                "activeConnections", userSessions.size()
        );

        // Send heartbeat to all connected users
        userSessions.values().forEach(session -> {
            try {
                messagingTemplate.convertAndSendToUser(
                        session.getUserId(),
                        "/queue/heartbeat",
                        heartbeat
                );
                session.setLastActivity(LocalDateTime.now());
            } catch (Exception e) {
                log.warn("Failed to send heartbeat to user {}: {}",
                        session.getUserId(), e.getMessage());
            }
        });
    }

    private void checkConnectionHealth() {
        int activeConnections = userSessions.size();
        int registryUsers = userRegistry.getUserCount();

        log.debug("📊 Connection Health - Sessions: {}, Registry Users: {}",
                activeConnections, registryUsers);

        // Alert if there's a significant discrepancy
        if (Math.abs(activeConnections - registryUsers) > 5) {
            log.warn("⚠️ Connection discrepancy detected - Sessions: {}, Registry: {}",
                    activeConnections, registryUsers);
        }
    }

    private void disconnectSession(String sessionId) {
        // Implementation to forcefully disconnect a session
        // This would require additional WebSocket session management
        log.info("🚫 Disconnecting unauthorized session: {}", sessionId);
    }

    // Public utility methods
    public boolean isUserOnline(String userId) {
        return userSessions.values().stream()
                .anyMatch(session -> session.getUserId().equals(userId) && session.isActive());
    }

    public Map<String, UserSession> getUserSessions() {
        return new ConcurrentHashMap<>(userSessions);
    }

    public int getActiveConnectionsCount() {
        return userSessions.size();
    }

    public Set<String> getOnlineUsers() {
        return userSessions.values().stream()
                .filter(UserSession::isActive)
                .map(UserSession::getUserId)
                .collect(java.util.stream.Collectors.toSet());
    }

    public void broadcastToDelivery(String deliveryId, Object message) {
        messagingTemplate.convertAndSend("/topic/delivery." + deliveryId, message);
    }

    // Graceful shutdown
    @PreDestroy
    public void shutdown() {
        log.info("🔄 Shutting down WebSocket event listener...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Notify all users about server shutdown
        Map<String, Object> shutdownNotice = Map.of(
                "type", "SERVER_SHUTDOWN",
                "message", "Server is shutting down. Please reconnect shortly.",
                "timestamp", LocalDateTime.now()
        );

        userSessions.values().forEach(session -> {
            messagingTemplate.convertAndSendToUser(
                    session.getUserId(),
                    "/queue/system",
                    shutdownNotice
            );
        });
    }

    // Inner class for enhanced session tracking - FIXED: Proper Lombok annotations
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSession {
        private String sessionId;
        private String userId;
        private LocalDateTime connectedAt;
        private LocalDateTime lastActivity;
        private boolean isActive;

        // Manual builder method as alternative to @Builder
        public static UserSession create(String sessionId, String userId) {
            UserSession session = new UserSession();
            session.setSessionId(sessionId);
            session.setUserId(userId);
            session.setConnectedAt(LocalDateTime.now());
            session.setLastActivity(LocalDateTime.now());
            session.setActive(true);
            return session;
        }

        public void updateActivity() {
            this.lastActivity = LocalDateTime.now();
        }
    }
}