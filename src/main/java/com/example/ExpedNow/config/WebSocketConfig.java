package com.example.ExpedNow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
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
                .setAllowedOriginPatterns("*")
                .addInterceptors(handshakeInterceptor)
                .setHandshakeHandler(new DefaultHandshakeHandler())
                .withSockJS()
                .setInterceptors(handshakeInterceptor); // ← AJOUT CRITIQUE

    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
        registration.taskExecutor()
                .corePoolSize(4)
                .maxPoolSize(8)
                .queueCapacity(100);
    }


    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue", "/user");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }
}