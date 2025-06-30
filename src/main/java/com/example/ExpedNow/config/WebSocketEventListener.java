    package com.example.ExpedNow.config;

    import lombok.extern.slf4j.Slf4j;
    import org.springframework.context.event.EventListener;
    import org.springframework.messaging.simp.SimpMessagingTemplate;
    import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
    import org.springframework.security.core.Authentication;
    import org.springframework.stereotype.Component;
    import org.springframework.web.socket.messaging.SessionConnectedEvent;
    import org.springframework.web.socket.messaging.SessionDisconnectEvent;

    import java.util.concurrent.ConcurrentHashMap;
    import java.util.Map;

    @Component
    @Slf4j
    public class WebSocketEventListener {

        private final SimpMessagingTemplate messagingTemplate;
        private final Map<String, String> userSessions = new ConcurrentHashMap<>();

        public WebSocketEventListener(SimpMessagingTemplate messagingTemplate) {
            this.messagingTemplate = messagingTemplate;
        }

        @EventListener
        public void handleWebSocketConnectListener(SessionConnectedEvent event) {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();
            Authentication authentication = (Authentication) headerAccessor.getUser();

            if (authentication != null) {
                // The principal is the userId (String) from your WebSocketAuthInterceptor
                String userId = authentication.getName(); // This gets the userId string

                userSessions.put(sessionId, userId);
                log.info("User {} connected with session {}", userId, sessionId);

                // Notify others that this user is now online
                messagingTemplate.convertAndSend("/topic/user.status." + userId,
                        Map.of("userId", userId, "status", "ONLINE"));
            } else {
                log.warn("WebSocket connection without authentication for session: {}", sessionId);
            }
        }

        @EventListener
        public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = headerAccessor.getSessionId();
            String userId = userSessions.remove(sessionId);

            if (userId != null) {
                log.info("User {} disconnected from session {}", userId, sessionId);

                // Notify others that this user is now offline
                messagingTemplate.convertAndSend("/topic/user.status." + userId,
                        Map.of("userId", userId, "status", "OFFLINE"));
            }
        }

        public boolean isUserOnline(String userId) {
            return userSessions.containsValue(userId);
        }

        public Map<String, String> getUserSessions() {
            return new ConcurrentHashMap<>(userSessions);
        }
    }