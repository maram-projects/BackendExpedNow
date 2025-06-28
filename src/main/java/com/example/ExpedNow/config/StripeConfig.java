package com.example.ExpedNow.config;

import com.stripe.Stripe;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "stripe")
public class StripeConfig {

    private String apiKey;
    private String publishableKey;
    private String currency = "usd"; // Default to USD
    private Webhook webhook = new Webhook();

    @PostConstruct
    public void init() {
        // Load from .env as fallback (if not set in application.yml)
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        if (this.apiKey == null || this.apiKey.isEmpty()) {
            this.apiKey = dotenv.get("STRIPE_SECRET_KEY");
        }

        if (this.publishableKey == null || this.publishableKey.isEmpty()) {
            this.publishableKey = dotenv.get("STRIPE_PUBLISHABLE_KEY");
        }

        // Load webhook secret from .env if not already set
        if (this.webhook.getSecret() == null || this.webhook.getSecret().isEmpty()) {
            this.webhook.setSecret(dotenv.get("STRIPE_WEBHOOK_SECRET"));
        }

        // Initialize Stripe
        Stripe.apiKey = this.apiKey;
        System.out.println("Stripe initialized with key: " + (this.apiKey != null));
        System.out.println("Stripe webhook secret loaded: " + (this.webhook.getSecret() != null && !this.webhook.getSecret().isEmpty()));
    }

    // Getters and Setters
    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getPublishableKey() {
        return publishableKey;
    }

    public void setPublishableKey(String publishableKey) {
        this.publishableKey = publishableKey;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Webhook getWebhook() {
        return webhook;
    }

    public void setWebhook(Webhook webhook) {
        this.webhook = webhook;
    }

    // Inner class for webhook configuration
    public static class Webhook {
        private String secret;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}