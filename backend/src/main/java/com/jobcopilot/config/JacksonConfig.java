package com.jobcopilot.config;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * Configures Jackson to serialize all {@link java.time.LocalDateTime} values in ISO-8601 format
 * with a trailing Z (UTC) indicator.
 */
@Configuration
public class JacksonConfig {

    private static final String ISO_UTC = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer utcDateTimeCustomizer() {
        return builder -> {
            builder.serializerByType(java.time.LocalDateTime.class,
                    new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(ISO_UTC)));
            builder.serializerByType(java.time.LocalDate.class,
                    new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer(
                            DateTimeFormatter.ISO_LOCAL_DATE));
        };
    }
}
