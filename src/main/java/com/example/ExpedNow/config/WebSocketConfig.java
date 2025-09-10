package com.example.ExpedNow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;
    private final WebSocketHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(WebSocketAuthInterceptor authInterceptor,
                           WebSocketHandshakeInterceptor handshakeInterceptor) {
        this.authInterceptor = authInterceptor;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:4200", "https://your-domain.com")
                .addInterceptors(handshakeInterceptor)
                .withSockJS()
                // Enhanced SockJS configuration for better performance
                .setStreamBytesLimit(512 * 1024) // 512KB
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30 * 1000) // 30 seconds
                .setHeartbeatTime(25 * 1000) // 25 seconds heartbeat
                .setClientLibraryUrl("https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js");

        // Also add a direct WebSocket endpoint for better performance when SockJS is not needed
        registry.addEndpoint("/ws-direct")
                .setAllowedOriginPatterns("http://localhost:4200", "https://your-domain.com")
                .addInterceptors(handshakeInterceptor);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);

        // Enhanced thread pool configuration for better performance
        registration.taskExecutor()
                .corePoolSize(8)          // Increased from 4
                .maxPoolSize(16)          // Increased from 8
                .queueCapacity(200)       // Increased from 100
                .keepAliveSeconds(60);    // Keep threads alive longer
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // Configure outbound channel for better message delivery performance
        registration.taskExecutor()
                .corePoolSize(8)
                .maxPoolSize(16)
                .queueCapacity(200)
                .keepAliveSeconds(60);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enhanced simple broker configuration
        config.enableSimpleBroker("/topic", "/queue", "/user")
                // Configure task scheduler for better performance
                .setTaskScheduler(taskScheduler())
                // Set heartbeat for connection health monitoring
                .setHeartbeatValue(new long[]{25000, 25000}); // 25 seconds client-to-server and server-to-client

        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");

        // Configure message size limits
        config.setCacheLimit(4096);  // Increased cache limit

        // Enable preserve publish order for consistency
        config.setPreservePublishOrder(true);
    }

    // Custom task scheduler for better WebSocket performance
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("websocket-scheduler-");
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();
        return scheduler;
    }

    // Alternative: Create separate beans for inbound/outbound executors if you need custom thread names
    @Bean
    public ThreadPoolTaskScheduler inboundChannelExecutor() {
        ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
        executor.setPoolSize(8);  // FIXED: Use setPoolSize instead of setCorePoolSize
        executor.setThreadNamePrefix("websocket-inbound-");
        executor.setAwaitTerminationSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    @Bean
    public ThreadPoolTaskScheduler outboundChannelExecutor() {
        ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
        executor.setPoolSize(8);  // FIXED: Use setPoolSize instead of setCorePoolSize
        executor.setThreadNamePrefix("websocket-outbound-");
        executor.setAwaitTerminationSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}