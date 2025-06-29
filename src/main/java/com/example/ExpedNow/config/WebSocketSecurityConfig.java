package com.example.ExpedNow.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSocketSecurityConfig {

    // WebSocket security is handled by WebSocketAuthInterceptor
    // which is registered in WebSocketConfig.configureClientInboundChannel()
    // This is the correct pattern for Spring Security 6.x

    // No additional configuration needed here - your interceptor approach is correct

}