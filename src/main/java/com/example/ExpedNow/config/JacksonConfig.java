package com.example.ExpedNow.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        JavaTimeModule module = new JavaTimeModule();
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer());
        mapper.registerModule(module);
        return mapper;
    }

    public static class LocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .optionalStart()
                .appendOffsetId()
                .optionalEnd()
                .toFormatter();

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            String value = p.getText();

            // Handle null or empty
            if (value == null || value.trim().isEmpty()) {
                return null;
            }

            // Try parsing with timezone first
            try {
                return LocalDateTime.parse(value, FORMATTER);
            } catch (Exception e) {
                // Fallback to without timezone
                try {
                    if (value.length() == 10) { // Just date
                        return LocalDateTime.parse(value + "T00:00:00");
                    }
                    return LocalDateTime.parse(value);
                } catch (Exception ex) {
                    throw new IOException("Failed to parse LocalDateTime from: " + value, ex);
                }
            }
        }
    }
}