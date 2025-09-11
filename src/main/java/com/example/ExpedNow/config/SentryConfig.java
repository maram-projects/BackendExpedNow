package com.example.ExpedNow.config;

import io.sentry.Sentry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SentryConfig {

    private static final Logger logger = LoggerFactory.getLogger(SentryConfig.class);

    @Value("${sentry.dsn:}")
    private String dsn;

    @PostConstruct
    public void initSentry() {
        if (!dsn.isEmpty()) {
            Sentry.init(options -> {
                options.setDsn(dsn);
                options.setEnvironment("development");
                options.setTracesSampleRate(1.0);
                options.setDebug(true);
            });
            logger.info("✅ Sentry initialized manually with DSN: {}", dsn);
        } else {
            logger.warn("⚠️ Sentry DSN is empty!");
        }
    }
}